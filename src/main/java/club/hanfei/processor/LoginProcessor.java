
package club.hanfei.processor;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import club.hanfei.model.Common;
import club.hanfei.model.Invitecode;
import club.hanfei.model.Notification;
import club.hanfei.model.Option;
import club.hanfei.model.Permission;
import club.hanfei.model.Pointtransfer;
import club.hanfei.model.Tag;
import club.hanfei.model.UserExt;
import club.hanfei.model.Verifycode;
import club.hanfei.processor.advice.CSRFToken;
import club.hanfei.processor.advice.LoginCheck;
import club.hanfei.processor.advice.PermissionGrant;
import club.hanfei.processor.advice.stopwatch.StopwatchEndAdvice;
import club.hanfei.processor.advice.stopwatch.StopwatchStartAdvice;
import club.hanfei.processor.advice.validate.UserForgetPwdValidation;
import club.hanfei.processor.advice.validate.UserRegister2Validation;
import club.hanfei.processor.advice.validate.UserRegisterValidation;
import club.hanfei.service.DataModelService;
import club.hanfei.service.InvitecodeMgmtService;
import club.hanfei.service.InvitecodeQueryService;
import club.hanfei.service.NotificationMgmtService;
import club.hanfei.service.OptionQueryService;
import club.hanfei.service.PointtransferMgmtService;
import club.hanfei.service.RoleQueryService;
import club.hanfei.service.TagQueryService;
import club.hanfei.service.UserMgmtService;
import club.hanfei.service.UserQueryService;
import club.hanfei.service.VerifycodeMgmtService;
import club.hanfei.service.VerifycodeQueryService;
import club.hanfei.util.Hanfei;
import club.hanfei.util.Sessions;
import com.qiniu.util.Auth;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.servlet.HttpMethod;
import org.b3log.latke.servlet.RequestContext;
import org.b3log.latke.servlet.annotation.After;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Requests;
import org.json.JSONObject;

/**
 * Login/Register processor.
 * <ul>
 * <li>Registration (/register), GET/POST</li>
 * <li>Login (/login), GET/POST</li>
 * <li>Logout (/logout), GET</li>
 * <li>Reset password (/reset-pwd), GET/POST</li>
 * </ul>
 *
  @version 1.13.12.5, Aug 7, 2018
 * @since 0.2.0
 */
@RequestProcessor
public class LoginProcessor {

    /**
     * Wrong password tries.
     * <p>
     * &lt;userId, {"wrongCount": int, "captcha": ""}&gt;
     * </p>
     */
    public static final Map<String, JSONObject> WRONG_PWD_TRIES = new ConcurrentHashMap<>();

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(LoginProcessor.class);

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Verifycode management service.
     */
    @Inject
    private VerifycodeMgmtService verifycodeMgmtService;

    /**
     * Verifycode query service.
     */
    @Inject
    private VerifycodeQueryService verifycodeQueryService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Invitecode query service.
     */
    @Inject
    private InvitecodeQueryService invitecodeQueryService;

    /**
     * Invitecode management service.
     */
    @Inject
    private InvitecodeMgmtService invitecodeMgmtService;

    /**
     * Invitecode management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

    /**
     * Role query service.
     */
    @Inject
    private RoleQueryService roleQueryService;

    /**
     * Tag query service.
     */
    @Inject
    private TagQueryService tagQueryService;

    /**
     * Next guide step.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/guide/next", method = HttpMethod.POST)
    @Before({LoginCheck.class})
    public void nextGuideStep(final RequestContext context) {
        context.renderJSON();

        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());

            return;
        }

        final HttpServletRequest request = context.getRequest();
        JSONObject user = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        final String userId = user.optString(Keys.OBJECT_ID);

        int step = requestJSONObject.optInt(UserExt.USER_GUIDE_STEP);

        if (UserExt.USER_GUIDE_STEP_STAR_PROJECT < step || UserExt.USER_GUIDE_STEP_FIN >= step) {
            step = UserExt.USER_GUIDE_STEP_FIN;
        }

        try {
            user = userQueryService.getUser(userId);
            user.put(UserExt.USER_GUIDE_STEP, step);
            userMgmtService.updateUser(userId, user);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Guide next step [" + step + "] failed", e);

            return;
        }

        context.renderJSON(true);
    }

    /**
     * Shows guide page.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/guide", method = HttpMethod.GET)
    @Before({StopwatchStartAdvice.class, LoginCheck.class})
    @After({CSRFToken.class, PermissionGrant.class, StopwatchEndAdvice.class})
    public void showGuide(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final JSONObject currentUser = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        final int step = currentUser.optInt(UserExt.USER_GUIDE_STEP);
        if (UserExt.USER_GUIDE_STEP_FIN == step) {
            context.sendRedirect(Latkes.getServePath());

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("verify/guide.ftl");

        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.CURRENT_USER, currentUser);

        final List<JSONObject> tags = tagQueryService.getTags(32);
        dataModel.put(Tag.TAGS, tags);

        final List<JSONObject> users = userQueryService.getNiceUsers(6);
        final Iterator<JSONObject> iterator = users.iterator();
        while (iterator.hasNext()) {
            final JSONObject user = iterator.next();
            if (user.optString(Keys.OBJECT_ID).equals(currentUser.optString(Keys.OBJECT_ID))) {
                iterator.remove();

                break;
            }
        }
        dataModel.put(User.USERS, users);

        // Qiniu file upload authenticate
        final Auth auth = Auth.create(Hanfei.get("qiniu.accessKey"), Hanfei.get("qiniu.secretKey"));
        final String uploadToken = auth.uploadToken(Hanfei.get("qiniu.bucket"));
        dataModel.put("qiniuUploadToken", uploadToken);
        dataModel.put("qiniuDomain", Hanfei.get("qiniu.domain"));

        if (!Hanfei.getBoolean("qiniu.enabled")) {
            dataModel.put("qiniuUploadToken", "");
        }

        final long imgMaxSize = Hanfei.getLong("upload.img.maxSize");
        dataModel.put("imgMaxSize", imgMaxSize);
        final long fileMaxSize = Hanfei.getLong("upload.file.maxSize");
        dataModel.put("fileMaxSize", fileMaxSize);

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Shows login page.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/login", method = HttpMethod.GET)
    @Before(StopwatchStartAdvice.class)
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showLogin(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        if (null != request.getAttribute(Common.CURRENT_USER)) {
            context.sendRedirect(Latkes.getServePath());

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);

        String referer = request.getParameter(Common.GOTO);
        if (StringUtils.isBlank(referer)) {
            referer = request.getHeader("referer");
        }

        if (!StringUtils.startsWith(referer, Latkes.getServePath())) {
            referer = Latkes.getServePath();
        }

        renderer.setTemplateName("verify/login.ftl");

        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.GOTO, referer);

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Shows forget password page.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/forget-pwd", method = HttpMethod.GET)
    @Before(StopwatchStartAdvice.class)
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showForgetPwd(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();
        renderer.setTemplateName("verify/forget-pwd.ftl");

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Forget password.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/forget-pwd", method = HttpMethod.POST)
    @Before(UserForgetPwdValidation.class)
    public void forgetPwd(final RequestContext context) {
        context.renderJSON();

        final HttpServletRequest request = context.getRequest();
        final JSONObject requestJSONObject = (JSONObject) request.getAttribute(Keys.REQUEST);
        final String email = requestJSONObject.optString(User.USER_EMAIL);

        try {
            final JSONObject user = userQueryService.getUserByEmail(email);
            if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) {
                context.renderFalseResult().renderMsg(langPropsService.get("notFoundUserLabel"));

                return;
            }

            final String userId = user.optString(Keys.OBJECT_ID);

            final JSONObject verifycode = new JSONObject();
            verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_RESET_PWD);
            final String code = RandomStringUtils.randomAlphanumeric(6);
            verifycode.put(Verifycode.CODE, code);
            verifycode.put(Verifycode.EXPIRED, DateUtils.addDays(new Date(), 1).getTime());
            verifycode.put(Verifycode.RECEIVER, email);
            verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
            verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_EMAIL);
            verifycode.put(Verifycode.USER_ID, userId);
            verifycodeMgmtService.addVerifycode(verifycode);

            context.renderTrueResult().renderMsg(langPropsService.get("verifycodeSentLabel"));
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("resetPwdLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[email=" + email + "]");

            context.renderMsg(msg);
        }
    }

    /**
     * Shows reset password page.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/reset-pwd", method = HttpMethod.GET)
    @Before(StopwatchStartAdvice.class)
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showResetPwd(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();

        final String code = request.getParameter("code");
        final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
        if (null == verifycode) {
            dataModel.put(Keys.MSG, langPropsService.get("verifycodeExpiredLabel"));
            renderer.setTemplateName("error/custom.ftl");
        } else {
            renderer.setTemplateName("verify/reset-pwd.ftl");

            final String userId = verifycode.optString(Verifycode.USER_ID);
            final JSONObject user = userQueryService.getUser(userId);
            dataModel.put(User.USER, user);
            dataModel.put(Common.CODE, code);
        }

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Resets password.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/reset-pwd", method = HttpMethod.POST)
    public void resetPwd(final RequestContext context) {
        context.renderJSON();

        final HttpServletResponse response = context.getResponse();
        final JSONObject requestJSONObject = context.requestJSON();
        final String password = requestJSONObject.optString(User.USER_PASSWORD); // Hashed
        final String userId = requestJSONObject.optString(UserExt.USER_T_ID);
        final String code = requestJSONObject.optString(Common.CODE);
        final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
        if (null == verifycode || !verifycode.optString(Verifycode.USER_ID).equals(userId)) {
            context.renderMsg(langPropsService.get("verifycodeExpiredLabel"));

            return;
        }

        String name = null;
        String email = null;
        try {
            final JSONObject user = userQueryService.getUser(userId);
            if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) {
                context.renderMsg(langPropsService.get("resetPwdLabel") + " - " + "User Not Found");

                return;
            }

            user.put(User.USER_PASSWORD, password);
            userMgmtService.updatePassword(user);
            verifycodeMgmtService.removeByCode(code);
            context.renderTrueResult();
            LOGGER.info("User [email=" + user.optString(User.USER_EMAIL) + "] reseted password");

            Sessions.login(response, userId, true);
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("resetPwdLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[name={0}, email={1}]", name, email);

            context.renderMsg(msg);
        }
    }

    /**
     * Shows registration page.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/register", method = HttpMethod.GET)
    @Before(StopwatchStartAdvice.class)
    @After({PermissionGrant.class, StopwatchEndAdvice.class})
    public void showRegister(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        if (null != request.getAttribute(Common.CURRENT_USER)) {
            context.sendRedirect(Latkes.getServePath());

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.REFERRAL, "");

        boolean useInvitationLink = false;

        String referral = request.getParameter("r");
        if (!UserRegisterValidation.invalidUserName(referral)) {
            final JSONObject referralUser = userQueryService.getUserByName(referral);
            if (null != referralUser) {
                dataModel.put(Common.REFERRAL, referral);

                final Map<String, JSONObject> permissions =
                        roleQueryService.getUserPermissionsGrantMap(referralUser.optString(Keys.OBJECT_ID));
                final JSONObject useILPermission =
                        permissions.get(Permission.PERMISSION_ID_C_COMMON_USE_INVITATION_LINK);
                useInvitationLink = useILPermission.optBoolean(Permission.PERMISSION_T_GRANT);
            }
        }

        final String code = request.getParameter("code");
        if (StringUtils.isBlank(code)) { // Register Step 1
            renderer.setTemplateName("verify/register.ftl");
        } else { // Register Step 2
            final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
            if (null == verifycode) {
                dataModel.put(Keys.MSG, langPropsService.get("verifycodeExpiredLabel"));
                renderer.setTemplateName("error/custom.ftl");
            } else {
                renderer.setTemplateName("verify/register2.ftl");

                final String userId = verifycode.optString(Verifycode.USER_ID);
                final JSONObject user = userQueryService.getUser(userId);
                dataModel.put(User.USER, user);

                if (UserExt.USER_STATUS_C_VALID == user.optInt(UserExt.USER_STATUS)
                        || UserExt.NULL_USER_NAME.equals(user.optString(User.USER_NAME))) {
                    dataModel.put(Keys.MSG, langPropsService.get("userExistLabel"));
                    renderer.setTemplateName("error/custom.ftl");
                } else {
                    referral = StringUtils.substringAfter(code, "r=");
                    if (StringUtils.isNotBlank(referral)) {
                        dataModel.put(Common.REFERRAL, referral);
                    }
                }
            }
        }

        final String allowRegister = optionQueryService.getAllowRegister();
        dataModel.put(Option.ID_C_MISC_ALLOW_REGISTER, allowRegister);
        if (useInvitationLink && "2".equals(allowRegister)) {
            dataModel.put(Option.ID_C_MISC_ALLOW_REGISTER, "1");
        }

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Register Step 1.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/register", method = HttpMethod.POST)
    @Before(UserRegisterValidation.class)
    public void register(final RequestContext context) {
        context.renderJSON();
        final HttpServletRequest request = context.getRequest();
        final JSONObject requestJSONObject = (JSONObject) request.getAttribute(Keys.REQUEST);
        final String name = requestJSONObject.optString(User.USER_NAME);
        final String email = requestJSONObject.optString(User.USER_EMAIL);
        final String invitecode = requestJSONObject.optString(Invitecode.INVITECODE);
        final String referral = requestJSONObject.optString(Common.REFERRAL);

        final JSONObject user = new JSONObject();
        user.put(User.USER_NAME, name);
        user.put(User.USER_EMAIL, email);
        user.put(User.USER_PASSWORD, "");
        final Locale locale = Locales.getLocale();
        user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "_" + locale.getCountry());

        try {
            final String newUserId = userMgmtService.addUser(user);

            final JSONObject verifycode = new JSONObject();
            verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_REGISTER);
            String code = RandomStringUtils.randomAlphanumeric(6);
            if (StringUtils.isNotBlank(referral)) {
                code += "r=" + referral;
            }
            verifycode.put(Verifycode.CODE, code);
            verifycode.put(Verifycode.EXPIRED, DateUtils.addDays(new Date(), 1).getTime());
            verifycode.put(Verifycode.RECEIVER, email);
            verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
            verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_EMAIL);
            verifycode.put(Verifycode.USER_ID, newUserId);
            verifycodeMgmtService.addVerifycode(verifycode);

            final String allowRegister = optionQueryService.getAllowRegister();
            if ("2".equals(allowRegister) && StringUtils.isNotBlank(invitecode)) {
                final JSONObject ic = invitecodeQueryService.getInvitecode(invitecode);
                ic.put(Invitecode.USER_ID, newUserId);
                ic.put(Invitecode.USE_TIME, System.currentTimeMillis());
                final String icId = ic.optString(Keys.OBJECT_ID);

                invitecodeMgmtService.updateInvitecode(icId, ic);
            }

            context.renderTrueResult().renderMsg(langPropsService.get("verifycodeSentLabel"));
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[name={0}, email={1}]", name, email);

            context.renderMsg(msg);
        }
    }

    /**
     * Register Step 2.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/register2", method = HttpMethod.POST)
    @Before(UserRegister2Validation.class)
    public void register2(final RequestContext context) {
        context.renderJSON();

        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();
        final JSONObject requestJSONObject = (JSONObject) request.getAttribute(Keys.REQUEST);

        final String password = requestJSONObject.optString(User.USER_PASSWORD); // Hashed
        final int appRole = requestJSONObject.optInt(UserExt.USER_APP_ROLE);
        final String referral = requestJSONObject.optString(Common.REFERRAL);
        final String userId = requestJSONObject.optString(UserExt.USER_T_ID);

        String name = null;
        String email = null;
        try {
            final JSONObject user = userQueryService.getUser(userId);
            if (null == user) {
                context.renderMsg(langPropsService.get("registerFailLabel") + " - " + "User Not Found");

                return;
            }

            name = user.optString(User.USER_NAME);
            email = user.optString(User.USER_EMAIL);

            user.put(UserExt.USER_APP_ROLE, appRole);
            user.put(User.USER_PASSWORD, password);
            user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);

            userMgmtService.addUser(user);

            Sessions.login(response, userId, false);

            final String ip = Requests.getRemoteAddr(request);
            userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true, true);

            if (StringUtils.isNotBlank(referral) && !UserRegisterValidation.invalidUserName(referral)) {
                final JSONObject referralUser = userQueryService.getUserByName(referral);
                if (null != referralUser) {
                    final String referralId = referralUser.optString(Keys.OBJECT_ID);
                    // Point
                    pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, userId,
                            Pointtransfer.TRANSFER_TYPE_C_INVITED_REGISTER,
                            Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER, referralId, System.currentTimeMillis(), "");
                    pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, referralId,
                            Pointtransfer.TRANSFER_TYPE_C_INVITE_REGISTER,
                            Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER, userId, System.currentTimeMillis(), "");

                    final JSONObject notification = new JSONObject();
                    notification.put(Notification.NOTIFICATION_USER_ID, referralId);
                    notification.put(Notification.NOTIFICATION_DATA_ID, userId);

                    notificationMgmtService.addInvitationLinkUsedNotification(notification);
                }
            }

            final JSONObject ic = invitecodeQueryService.getInvitecodeByUserId(userId);
            if (null != ic && Invitecode.STATUS_C_UNUSED == ic.optInt(Invitecode.STATUS)) {
                ic.put(Invitecode.STATUS, Invitecode.STATUS_C_USED);
                ic.put(Invitecode.USER_ID, userId);
                ic.put(Invitecode.USE_TIME, System.currentTimeMillis());
                final String icId = ic.optString(Keys.OBJECT_ID);

                invitecodeMgmtService.updateInvitecode(icId, ic);

                final String icGeneratorId = ic.optString(Invitecode.GENERATOR_ID);
                if (StringUtils.isNotBlank(icGeneratorId) && !Pointtransfer.ID_C_SYS.equals(icGeneratorId)) {
                    pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, icGeneratorId,
                            Pointtransfer.TRANSFER_TYPE_C_INVITECODE_USED,
                            Pointtransfer.TRANSFER_SUM_C_INVITECODE_USED, userId, System.currentTimeMillis(), "");

                    final JSONObject notification = new JSONObject();
                    notification.put(Notification.NOTIFICATION_USER_ID, icGeneratorId);
                    notification.put(Notification.NOTIFICATION_DATA_ID, userId);

                    notificationMgmtService.addInvitecodeUsedNotification(notification);
                }
            }

            context.renderTrueResult();

            LOGGER.log(Level.INFO, "Registered a user [name={0}, email={1}]", name, email);
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + " [name={0}, email={1}]", name, email);

            context.renderMsg(msg);
        }
    }

    /**
     * Logins user.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/login", method = HttpMethod.POST)
    public void login(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        context.renderJSON().renderMsg(langPropsService.get("loginFailLabel"));

        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
        } catch (final Exception e) {
            context.renderMsg(langPropsService.get("paramsParseFailedLabel"));

            return;
        }

        final String nameOrEmail = requestJSONObject.optString("nameOrEmail");

        try {
            JSONObject user = userQueryService.getUserByName(nameOrEmail);
            if (null == user) {
                user = userQueryService.getUserByEmail(nameOrEmail);
            }

            if (null == user) {
                context.renderMsg(langPropsService.get("notFoundUserLabel"));

                return;
            }

            if (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)) {
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false, true);
                context.renderMsg(langPropsService.get("userBlockLabel"));

                return;
            }

            if (UserExt.USER_STATUS_C_NOT_VERIFIED == user.optInt(UserExt.USER_STATUS)) {
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false, true);
                context.renderMsg(langPropsService.get("notVerifiedLabel"));

                return;
            }

            if (UserExt.USER_STATUS_C_INVALID_LOGIN == user.optInt(UserExt.USER_STATUS)
                    || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS)) {
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false, true);
                context.renderMsg(langPropsService.get("invalidLoginLabel"));

                return;
            }

            final String userId = user.optString(Keys.OBJECT_ID);
            JSONObject wrong = WRONG_PWD_TRIES.get(userId);
            if (null == wrong) {
                wrong = new JSONObject();
            }

            final int wrongCount = wrong.optInt(Common.WRON_COUNT);
            if (wrongCount > 3) {
                final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);
                if (!StringUtils.equals(wrong.optString(CaptchaProcessor.CAPTCHA), captcha)) {
                    context.renderMsg(langPropsService.get("captchaErrorLabel"));
                    context.renderJSONValue(Common.NEED_CAPTCHA, userId);

                    return;
                }
            }

            final String userPassword = user.optString(User.USER_PASSWORD);
            if (userPassword.equals(requestJSONObject.optString(User.USER_PASSWORD))) {
                final String token = Sessions.login(response, userId, requestJSONObject.optBoolean(Common.REMEMBER_LOGIN));

                final String ip = Requests.getRemoteAddr(request);
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true, true);

                context.renderMsg("").renderTrueResult();
                context.renderJSONValue(Keys.TOKEN, token);

                WRONG_PWD_TRIES.remove(userId);

                return;
            }

            if (wrongCount > 2) {
                context.renderJSONValue(Common.NEED_CAPTCHA, userId);
            }

            wrong.put(Common.WRON_COUNT, wrongCount + 1);
            WRONG_PWD_TRIES.put(userId, wrong);

            context.renderMsg(langPropsService.get("wrongPwdLabel"));
        } catch (final ServiceException e) {
            context.renderMsg(langPropsService.get("loginFailLabel"));
        }
    }

    /**
     * Logout.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/logout", method = HttpMethod.GET)
    public void logout(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();

        final JSONObject user = (JSONObject) request.getAttribute(Common.CURRENT_USER);
        if (null != user) {
            Sessions.logout(user.optString(Keys.OBJECT_ID), context.getResponse());
        }

        String destinationURL = request.getParameter(Common.GOTO);
        if (StringUtils.isBlank(destinationURL)) {
            destinationURL = request.getHeader("referer");
        }

        context.sendRedirect(destinationURL);
    }
}