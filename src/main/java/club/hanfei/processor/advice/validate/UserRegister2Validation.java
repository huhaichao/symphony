
package club.hanfei.processor.advice.validate;

import javax.servlet.http.HttpServletRequest;

import club.hanfei.model.Option;
import club.hanfei.model.UserExt;
import club.hanfei.processor.LoginProcessor;
import club.hanfei.service.OptionQueryService;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.RequestContext;
import org.b3log.latke.servlet.advice.ProcessAdvice;
import org.b3log.latke.servlet.advice.RequestProcessAdviceException;
import org.json.JSONObject;

/**
 * UserRegister2Validation for validate {@link LoginProcessor} register2(Type POST) method.
 *
@version 1.1.1.0, Jul 3, 2016
 * @since 1.3.0
 */
@Singleton
public class UserRegister2Validation extends ProcessAdvice {

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Max password length.
     *
     * <p>
     * MD5 32
     * </p>
     */
    private static final int MAX_PWD_LENGTH = 32;

    /**
     * Min password length.
     */
    private static final int MIN_PWD_LENGTH = 1;

    @Override
    public void doAdvice(final RequestContext context) throws RequestProcessAdviceException {
        final HttpServletRequest request = context.getRequest();

        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
            request.setAttribute(Keys.REQUEST, requestJSONObject);

            // check if admin allow to register
            final JSONObject option = optionQueryService.getOption(Option.ID_C_MISC_ALLOW_REGISTER);
            if ("1".equals(option.optString(Option.OPTION_VALUE))) {
                throw new Exception(langPropsService.get("notAllowRegisterLabel"));
            }
        } catch (final Exception e) {
            throw new RequestProcessAdviceException(new JSONObject().put(Keys.MSG, e.getMessage()));
        }

        final int appRole = requestJSONObject.optInt(UserExt.USER_APP_ROLE);
        final String password = requestJSONObject.optString(User.USER_PASSWORD);
        checkField(UserExt.USER_APP_ROLE_C_HACKER != appRole
                && UserExt.USER_APP_ROLE_C_PAINTER != appRole, "registerFailLabel", "invalidAppRoleLabel");
        checkField(invalidUserPassword(password), "registerFailLabel", "invalidPasswordLabel");
    }

    /**
     * Checks password, length [1, 16].
     *
     * @param password the specific password
     * @return {@code true} if it is invalid, returns {@code false} otherwise
     */
    public static boolean invalidUserPassword(final String password) {
        return password.length() < MIN_PWD_LENGTH || password.length() > MAX_PWD_LENGTH;
    }

    /**
     * Checks field.
     *
     * @param invalid    the specified invalid flag
     * @param failLabel  the specified fail label
     * @param fieldLabel the specified field label
     * @throws RequestProcessAdviceException request process advice exception
     */
    private void checkField(final boolean invalid, final String failLabel, final String fieldLabel)
            throws RequestProcessAdviceException {
        if (invalid) {
            throw new RequestProcessAdviceException(new JSONObject().put(Keys.MSG, langPropsService.get(failLabel)
                    + " - " + langPropsService.get(fieldLabel)));
        }
    }
}
