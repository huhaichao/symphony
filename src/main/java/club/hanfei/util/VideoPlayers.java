
package club.hanfei.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.service.LangPropsService;

/**
 * Video player utilities.
 *
@version 1.0.1.2, Nov 16, 2018
 * @since 2.2.0
 */
public final class VideoPlayers {

    /**
     * Video URL regix.
     */
    private static final String VIDEO_URL_REGEX =
            "<p>( )*<a href.*\\.(rm|rmvb|3gp|avi|mpeg|mp4|wmv|mkv|dat|asf|flv|mov|webm).*</a>( )*</p>";

    /**
     * Video URL regex pattern.
     */
    private static final Pattern PATTERN = Pattern.compile(VIDEO_URL_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Renders the specified content with video player if need.
     *
     * @param content the specified content
     * @return rendered content
     */
    public static final String render(final String content) {
        final BeanManager beanManager = BeanManager.getInstance();
        final LangPropsService langPropsService = beanManager.getReference(LangPropsService.class);

        final StringBuffer contentBuilder = new StringBuffer();
        final Matcher m = PATTERN.matcher(content);

        while (m.find()) {
            final String g = m.group();
            String videoURL = StringUtils.substringBetween(g, "href=\"", "\" rel=");
            if (StringUtils.isBlank(videoURL)) {
                videoURL = StringUtils.substringBetween(g, "href=\"", "\"");
            }

            m.appendReplacement(contentBuilder, "<video width=\"100%\" src=\""
                    + videoURL + "\" controls=\"controls\">" + langPropsService.get("notSupportPlayLabel") + "</video>\n");
        }
        m.appendTail(contentBuilder);

        return contentBuilder.toString();
    }

    private VideoPlayers() {
    }
}
