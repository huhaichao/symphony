
package club.hanfei.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import club.hanfei.HanfeiServletListener;
import club.hanfei.model.Common;
import club.hanfei.model.Option;
import club.hanfei.service.OptionQueryService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.service.LangPropsService;
import org.json.JSONObject;

/**
 * Symphony utilities.
 *
@version 1.9.0.3, Dec 2, 2018
 * @since 0.1.0
 */
public final class Hanfei {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(Hanfei.class);

    /**
     * Configurations.
     */
    public static final Properties CFG = new Properties();

    /**
     * User-Agent.
     */
    public static final String USER_AGENT_BOT = "Sym/" + HanfeiServletListener.VERSION + "; +https://github.com/b3log/symphony";

    /**
     * Reserved tags.
     */
    public static final String[] RESERVED_TAGS;

    /**
     * White list - tags.
     */
    public static final String[] WHITE_LIST_TAGS;

    /**
     * Reserved user names.
     */
    public static final String[] RESERVED_USER_NAMES;

    /**
     * Thread pool.
     */
    public static final ThreadPoolExecutor EXECUTOR_SERVICE = (ThreadPoolExecutor) Executors.newFixedThreadPool(128);

    /**
     * Cron thread pool.
     */
    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(4);

    /**
     * Available processors.
     */
    public static final int PROCESSORS = Runtime.getRuntime().availableProcessors();

    static {
        try {
            InputStream resourceAsStream;
            final String symPropsEnv = System.getenv("SYM_PROPS");
            if (StringUtils.isNotBlank(symPropsEnv)) {
                LOGGER.trace("Loading hanfei.properties from env var [$SYM_PROPS=" + symPropsEnv + "]");
                resourceAsStream = new FileInputStream(symPropsEnv);
            } else {
                LOGGER.trace("Loading hanfei.properties from classpath [/hanfei.properties]");
                resourceAsStream = Latkes.class.getResourceAsStream("/hanfei.properties");
            }

            CFG.load(resourceAsStream);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Loads hanfei.properties failed, exited", e);

            System.exit(-1);
        }
    }

    static {
        // Loads reserved tags
        final String reservedTags = CFG.getProperty("reservedTags");
        final String[] tags = reservedTags.split(",");
        RESERVED_TAGS = new String[tags.length];

        for (int i = 0; i < tags.length; i++) {
            final String tag = tags[i];

            RESERVED_TAGS[i] = tag.trim();
        }

        // Loads white list tags
        final String whiteListTags = CFG.getProperty("whitelist.tags");
        final String[] wlTags = whiteListTags.split(",");
        WHITE_LIST_TAGS = new String[wlTags.length];

        for (int i = 0; i < wlTags.length; i++) {
            final String tag = wlTags[i];

            WHITE_LIST_TAGS[i] = tag.trim();
        }

        // Loads reserved usernames
        final String reservedUserNames = CFG.getProperty("reservedUserNames");
        final String[] userNames = reservedUserNames.split(",");
        RESERVED_USER_NAMES = new String[userNames.length];

        for (int i = 0; i < userNames.length; i++) {
            final String userName = userNames[i];

            RESERVED_USER_NAMES[i] = userName.trim();
        }
    }

    static {
        try {
            final SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[0], new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(final X509Certificate[] arg0, final String arg1) {
                }

                @Override
                public void checkServerTrusted(final X509Certificate[] arg0, final String arg1) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            }}, new SecureRandom());
            SSLContext.setDefault(ctx);
        } catch (final Exception e) {
            // ignore
        }

        // Reports status to Rhythm, I hope that everyone will be able to join in the SymHub plan :p
        new Timer(true).schedule(new TimerTask() {
            @Override
            public void run() {
                final String symURL = Latkes.getServePath();
                if (Networks.isIPv4(symURL)) {
                    return;
                }

                HttpURLConnection httpConn = null;
                try {
                    final BeanManager beanManager = BeanManager.getInstance();
                    final OptionQueryService optionQueryService = beanManager.getReference(OptionQueryService.class);

                    final JSONObject statistic = optionQueryService.getStatistic();
                    final int articleCount = statistic.optInt(Option.ID_C_STATISTIC_ARTICLE_COUNT);
                    if (articleCount < 66) {
                        return;
                    }

                    final LangPropsService langPropsService = beanManager.getReference(LangPropsService.class);

                    httpConn = (HttpURLConnection) new URL("https://rhythm.b3log.org/sym").openConnection();
                    httpConn.setConnectTimeout(10000);
                    httpConn.setReadTimeout(10000);
                    httpConn.setDoOutput(true);
                    httpConn.setRequestMethod("POST");
                    httpConn.setRequestProperty(Common.USER_AGENT, USER_AGENT_BOT);

                    httpConn.connect();

                    try (final OutputStream outputStream = httpConn.getOutputStream()) {
                        final JSONObject sym = new JSONObject();
                        sym.put("symURL", symURL);
                        sym.put("symTitle", langPropsService.get("symphonyLabel", Latkes.getLocale()));

                        IOUtils.write(sym.toString(), outputStream, "UTF-8");
                        outputStream.flush();
                    }

                    httpConn.getResponseCode();
                } catch (final Exception e) {
                    // ignore
                } finally {
                    if (null != httpConn) {
                        try {
                            httpConn.disconnect();
                        } catch (final Exception e) {
                            // ignore
                        }
                    }
                }
            }
        }, 1000 * 60 * 60 * 2, 1000 * 60 * 60 * 12);
    }

    /**
     * Gets the current process's id.
     *
     * @return the current process's id
     */
    public static long currentPID() {
        final String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();

        return Long.parseLong(processName.split("@")[0]);
    }

    /**
     * Gets active thread count of thread pool.
     *
     * @return active thread count
     */
    public static int getActiveThreadCount() {
        return EXECUTOR_SERVICE.getActiveCount();
    }

    /**
     * Gets the max thread count of thread pool.
     *
     * @return max thread count
     */
    public static int getMaxThreadCount() {
        return EXECUTOR_SERVICE.getMaximumPoolSize();
    }

    /**
     * Gets a configuration string property with the specified key.
     *
     * @param key the specified key
     * @return string property value corresponding to the specified key, returns {@code null} if not found
     */
    public static String get(final String key) {
        return CFG.getProperty(key);
    }

    /**
     * Gets a configuration boolean property with the specified key.
     *
     * @param key the specified key
     * @return boolean property value corresponding to the specified key, returns {@code null} if not found
     */
    public static Boolean getBoolean(final String key) {
        final String stringValue = get(key);
        if (null == stringValue) {
            return null;
        }

        return Boolean.valueOf(stringValue);
    }

    /**
     * Gets a configuration float property with the specified key.
     *
     * @param key the specified key
     * @return float property value corresponding to the specified key, returns {@code null} if not found
     */
    public static Float getFloat(final String key) {
        final String stringValue = get(key);
        if (null == stringValue) {
            return null;
        }

        return Float.valueOf(stringValue);
    }

    /**
     * Gets a configuration integer property with the specified key.
     *
     * @param key the specified key
     * @return integer property value corresponding to the specified key, returns {@code null} if not found
     */
    public static Integer getInt(final String key) {
        final String stringValue = get(key);
        if (null == stringValue) {
            return null;
        }

        return Integer.valueOf(stringValue);
    }

    /**
     * Gets a configuration long property with the specified key.
     *
     * @param key the specified key
     * @return long property value corresponding to the specified key, returns {@code null} if not found
     */
    public static Long getLong(final String key) {
        final String stringValue = get(key);
        if (null == stringValue) {
            return null;
        }

        return Long.valueOf(stringValue);
    }

    /**
     * Private constructor.
     */
    private Hanfei() {
    }
}
