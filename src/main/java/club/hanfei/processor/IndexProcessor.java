
package club.hanfei.processor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import club.hanfei.model.Article;
import club.hanfei.model.Common;
import club.hanfei.model.UserExt;
import club.hanfei.processor.advice.AnonymousViewCheck;
import club.hanfei.processor.advice.PermissionGrant;
import club.hanfei.processor.advice.stopwatch.StopwatchEndAdvice;
import club.hanfei.processor.advice.stopwatch.StopwatchStartAdvice;
import club.hanfei.service.ArticleQueryService;
import club.hanfei.service.DataModelService;
import club.hanfei.service.UserMgmtService;
import club.hanfei.service.UserQueryService;
import club.hanfei.util.Emotions;
import club.hanfei.util.Hanfei;
import club.hanfei.util.Markdowns;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HttpMethod;
import org.b3log.latke.servlet.RequestContext;
import org.b3log.latke.servlet.annotation.After;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Paginator;
import org.b3log.latke.util.Stopwatchs;
import org.json.JSONObject;

/**
 * Index processor.
 * <ul>
 * <li>Shows index (/), GET</li>
 * <li>Show recent articles (/recent), GET</li>
 * <li>Show question articles (/qna), GET</li>
 * <li>Show watch relevant pages (/watch/*), GET</li>
 * <li>Show hot articles (/hot), GET</li>
 * <li>Show perfect articles (/perfect), GET</li>
 * <li>Shows about (/about), GET</li>
 * <li>Shows b3log (/b3log), GET</li>
 * <li>Shows kill browser (/kill-browser), GET</li>
 * </ul>
 *
  @version 1.15.0.1, Sep 14, 2018
 * @since 0.2.0
 */
@RequestProcessor
public class IndexProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(IndexProcessor.class);

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Shows question articles.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = {"/qna", "/qna/unanswered", "/qna/reward", "/qna/hot"}, method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showQnA(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("qna.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final int pageNum = Paginator.getPage(request);
        int pageSize = Hanfei.getInt("indexArticlesCnt");
        final JSONObject user = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finshedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");

                return;
            }
        }

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);

        String sortModeStr = StringUtils.substringAfter(request.getRequestURI(), "/qna");
        int sortMode;
        switch (sortModeStr) {
            case "":
                sortMode = 0;

                break;
            case "/unanswered":
                sortMode = 1;

                break;
            case "/reward":
                sortMode = 2;

                break;
            case "/hot":
                sortMode = 3;

                break;
            default:
                sortMode = 0;
        }

        dataModel.put(Common.SELECTED, Common.QNA);
        final JSONObject result = articleQueryService.getQuestionArticles(avatarViewMode, sortMode, pageNum, pageSize);
        final List<JSONObject> allArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        dataModel.put(Common.LATEST_ARTICLES, allArticles);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);
        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.CURRENT, StringUtils.substringAfter(request.getRequestURI(), "/qna"));
    }

    /**
     * Shows watch articles or users.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = {"/watch", "/watch/users"}, method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showWatch(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("watch.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        int pageSize = Hanfei.getInt("indexArticlesCnt");
        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);
        final JSONObject user = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finshedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");

                return;
            }
        }

        dataModel.put(Common.WATCHING_ARTICLES, Collections.emptyList());
        String sortModeStr = StringUtils.substringAfter(request.getRequestURI(), "/watch");
        switch (sortModeStr) {
            case "":
                if (null != user) {
                    final List<JSONObject> followingTagArticles = articleQueryService.getFollowingTagArticles(
                            avatarViewMode, user.optString(Keys.OBJECT_ID), 1, pageSize);
                    dataModel.put(Common.WATCHING_ARTICLES, followingTagArticles);
                }

                break;
            case "/users":
                if (null != user) {
                    final List<JSONObject> followingUserArticles = articleQueryService.getFollowingUserArticles(
                            avatarViewMode, user.optString(Keys.OBJECT_ID), 1, pageSize);
                    dataModel.put(Common.WATCHING_ARTICLES, followingUserArticles);
                }

                break;
        }

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.SELECTED, Common.WATCH);
        dataModel.put(Common.CURRENT, StringUtils.substringAfter(request.getRequestURI(), "/watch"));
    }

    /**
     * Shows md guide.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/guide/markdown", method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class})
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showMDGuide(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("other/md-guide.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        try (final InputStream inputStream = IndexProcessor.class.getResourceAsStream("/md_guide.md")) {
            final String md = IOUtils.toString(inputStream, "UTF-8");
            String html = Emotions.convert(md);
            html = Markdowns.toHTML(html);

            dataModel.put("md", md);
            dataModel.put("html", html);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Loads markdown guide failed", e);
        }

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Shows index.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = {"", "/"}, method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class})
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showIndex(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();
        final JSONObject currentUser = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        if (null != currentUser) {
            final String indexRedirectURL = currentUser.optString(UserExt.USER_INDEX_REDIRECT_URL);
            if (StringUtils.isNotBlank(indexRedirectURL)) {
                try {
                    response.sendRedirect(indexRedirectURL);

                    return;
                } catch (final Exception e) {
                    LOGGER.log(Level.ERROR, "Sends index redirect for user [id=" + currentUser.optString(Keys.OBJECT_ID) + "] failed", e);
                }
            }
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);
        final List<JSONObject> recentArticles = articleQueryService.getIndexRecentArticles(avatarViewMode);
        dataModel.put(Common.RECENT_ARTICLES, recentArticles);

        final List<JSONObject> perfectArticles = articleQueryService.getIndexPerfectArticles();
        dataModel.put(Common.PERFECT_ARTICLES, perfectArticles);

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
        dataModelService.fillIndexTags(dataModel);

        dataModel.put(Common.SELECTED, Common.INDEX);
    }

    /**
     * Shows recent articles.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = {"/recent", "/recent/hot", "/recent/good", "/recent/reply"}, method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showRecent(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("recent.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final int pageNum = Paginator.getPage(request);
        int pageSize = Hanfei.getInt("indexArticlesCnt");
        final JSONObject user = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finshedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");

                return;
            }
        }

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);

        String sortModeStr = StringUtils.substringAfter(request.getRequestURI(), "/recent");
        int sortMode;
        switch (sortModeStr) {
            case "":
                sortMode = 0;

                break;
            case "/hot":
                sortMode = 1;

                break;
            case "/good":
                sortMode = 2;

                break;
            case "/reply":
                sortMode = 3;

                break;
            default:
                sortMode = 0;
        }

        dataModel.put(Common.SELECTED, Common.RECENT);
        final JSONObject result = articleQueryService.getRecentArticles(avatarViewMode, sortMode, pageNum, pageSize);
        final List<JSONObject> allArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        final List<JSONObject> stickArticles = new ArrayList<>();
        final Iterator<JSONObject> iterator = allArticles.iterator();
        while (iterator.hasNext()) {
            final JSONObject article = iterator.next();
            final boolean stick = article.optInt(Article.ARTICLE_T_STICK_REMAINS) > 0;
            article.put(Article.ARTICLE_T_IS_STICK, stick);
            if (stick) {
                stickArticles.add(article);
                iterator.remove();
            }
        }

        dataModel.put(Common.STICK_ARTICLES, stickArticles);
        dataModel.put(Common.LATEST_ARTICLES, allArticles);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);
        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.CURRENT, StringUtils.substringAfter(request.getRequestURI(), "/recent"));
    }

    /**
     * Shows hot articles.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/hot", method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showHotArticles(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("hot.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        int pageSize = Hanfei.getInt("indexArticlesCnt");
        final JSONObject user = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
        }

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);
        final List<JSONObject> indexArticles = articleQueryService.getHotArticles(avatarViewMode, pageSize);
        dataModel.put(Common.INDEX_ARTICLES, indexArticles);
        dataModel.put(Common.SELECTED, Common.HOT);

        Stopwatchs.start("Fills");
        try {
            dataModelService.fillHeaderAndFooter(request, response, dataModel);
            if (!(Boolean) dataModel.get(Common.IS_MOBILE)) {
                dataModelService.fillRandomArticles(dataModel);
            }
            dataModelService.fillSideHotArticles(dataModel);
            dataModelService.fillSideTags(dataModel);
            dataModelService.fillLatestCmts(dataModel);
        } finally {
            Stopwatchs.end();
        }
    }

    /**
     * Shows perfect articles.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/perfect", method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showPerfectArticles(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("perfect.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final int pageNum = Paginator.getPage(request);
        int pageSize = Hanfei.getInt("indexArticlesCnt");
        final JSONObject user = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
            if (!UserExt.finshedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");

                return;
            }
        }

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);
        final JSONObject result = articleQueryService.getPerfectArticles(avatarViewMode, pageNum, pageSize);
        final List<JSONObject> perfectArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        dataModel.put(Common.PERFECT_ARTICLES, perfectArticles);
        dataModel.put(Common.SELECTED, Common.PERFECT);
        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);
        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Shows about.
     *
     * @param context the specified context
     * @throws Exception exception
     */
    @RequestProcessing(value = "/about", method = HttpMethod.GET)
    @Before(StopwatchStartAdvice.class)
    @After(StopwatchEndAdvice.class)
    public void showAbout(final RequestContext context) throws Exception {
        context.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        context.setHeader("Location", "http://localhost:8080");
        final HttpServletResponse response = context.getResponse();
        response.flushBuffer();
    }

    /**
     * Shows b3log.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/b3log", method = HttpMethod.GET)
    @Before(StopwatchStartAdvice.class)
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showB3log(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("other/b3log.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Shows kill browser page with the specified context.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/kill-browser", method = HttpMethod.GET)
    @Before(StopwatchStartAdvice.class)
    @After(StopwatchEndAdvice.class)
    public void showKillBrowser(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        renderer.setTemplateName("other/kill-browser.ftl");
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = renderer.getDataModel();
        final Map<String, String> langs = langPropsService.getAll(Locales.getLocale());

        dataModel.putAll(langs);
        Keys.fillRuntime(dataModel);
        dataModelService.fillMinified(dataModel);
    }
}