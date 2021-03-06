
package club.hanfei.service;

import java.util.ArrayList;
import java.util.List;

import club.hanfei.model.Operation;
import club.hanfei.model.UserExt;
import club.hanfei.repository.OperationRepository;
import club.hanfei.repository.UserRepository;
import org.apache.commons.lang.time.DateFormatUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.CollectionUtils;
import org.b3log.latke.util.Paginator;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Operation query service.
 *
@version 1.0.0.0, Nov 19, 2018
 * @since 3.4.4
 */
@Service
public class OperationQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(OperationQueryService.class);

    /**
     * Operation repository.
     */
    @Inject
    private OperationRepository operationRepository;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Get audit logs by the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          {
     *                          "paginationCurrentPageNum": 1,
     *                          "paginationPageSize": 20,
     *                          "paginationWindowSize": 10
     *                          }, see {@link Pagination} for more details
     * @return for example,      <pre>
     * {
     *     "pagination": {
     *         "paginationPageCount": 100,
     *         "paginationPageNums": [1, 2, 3, 4, 5]
     *     },
     *     "operations": [{
     *         "oId": "",
     *         "operationUserName": "",
     *         "operationContent": "",
     *         "operationTime": "",
     *         "operationIP": "",
     *         "operationUA": ""
     *      }, ....]
     * }
     * </pre>
     * @see Pagination
     */
    public JSONObject getAuditlogs(final JSONObject requestJSONObject) {
        final JSONObject ret = new JSONObject();

        final int currentPageNum = requestJSONObject.optInt(Pagination.PAGINATION_CURRENT_PAGE_NUM);
        final int pageSize = requestJSONObject.optInt(Pagination.PAGINATION_PAGE_SIZE);
        final int windowSize = requestJSONObject.optInt(Pagination.PAGINATION_WINDOW_SIZE);
        final Query query = new Query().setCurrentPageNum(currentPageNum).setPageSize(pageSize).
                addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);

        JSONObject result;
        try {
            result = operationRepository.get(query);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get operations failed", e);

            return null;
        }

        final int pageCount = result.optJSONObject(Pagination.PAGINATION).optInt(Pagination.PAGINATION_PAGE_COUNT);
        final JSONObject pagination = new JSONObject();
        ret.put(Pagination.PAGINATION, pagination);
        final List<Integer> pageNums = Paginator.paginate(currentPageNum, pageSize, pageCount, windowSize);
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        final JSONArray data = result.optJSONArray(Keys.RESULTS);
        final List<JSONObject> records = CollectionUtils.jsonArrayToList(data);
        final List<JSONObject> auditlogs = new ArrayList<>();
        for (final JSONObject record : records) {
            final JSONObject auditlog = new JSONObject();
            auditlog.put(Keys.OBJECT_ID, record.optString(Keys.OBJECT_ID));
            try {
                final String operationUserId = record.optString(Operation.OPERATION_USER_ID);
                final JSONObject operationUser = userRepository.get(operationUserId);
                auditlog.put(Operation.OPERATION_T_USER_NAME, UserExt.getUserLink(operationUser));
                auditlog.put(Operation.OPERATION_T_TIME, DateFormatUtils.format(record.optLong(Keys.OBJECT_ID), "yyyy-MM-dd HH:mm:ss"));
                auditlog.put(Operation.OPERATION_IP, record.optString(Operation.OPERATION_IP));
                auditlog.put(Operation.OPERATION_UA, record.optString(Operation.OPERATION_UA));
                final int operationCode = record.optInt(Operation.OPERATION_CODE);
                final String operationContent = langPropsService.get("auditlog" + operationCode + "Label");
                auditlog.put(Operation.OPERATION_T_CONTENT, operationContent);
                auditlogs.add(auditlog);
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Builds audit log failed", e);

                continue;
            }
        }
        ret.put(Operation.OPERATIONS, auditlogs);

        return ret;
    }
}
