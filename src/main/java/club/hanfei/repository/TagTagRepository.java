
package club.hanfei.repository;

import java.util.ArrayList;
import java.util.List;

import club.hanfei.model.Common;
import club.hanfei.model.Tag;
import club.hanfei.util.Hanfei;
import org.b3log.latke.Keys;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.CompositeFilter;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.Filter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.annotation.Repository;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tag-Tag relation repository.
 *
@version 1.0.0.1, Oct 19, 2016
 * @since 1.3.0
 */
@Repository
public class TagTagRepository extends AbstractRepository {

    /**
     * Weight threshold.
     */
    private static final int WEIGHT = Hanfei.getInt("tagRelatedWeight");

    /**
     * Public constructor.
     */
    public TagTagRepository() {
        super(Tag.TAG + "_" + Tag.TAG);
    }

    /**
     * Gets tag-tag relations by the specified tag1 id.
     *
     * @param tag1Id         the specified tag1 id
     * @param currentPageNum the specified current page number, MUST greater then {@code 0}
     * @param pageSize       the specified page size(count of a page contains objects), MUST greater then {@code 0}
     * @return for example      <pre>
     * {
     *     "pagination": {
     *       "paginationPageCount": 88250
     *     },
     *     "rslts": [{
     *         "oId": "",
     *         "tag1_oId": tag1Id,
     *         "tag2_oId": "",
     *         "weight": int
     *     }, ....]
     * }
     * </pre>
     * @throws RepositoryException repository exception
     */
    public JSONObject getByTag1Id(final String tag1Id, final int currentPageNum, final int pageSize)
            throws RepositoryException {
        final List<Filter> filters = new ArrayList<>();
        filters.add(new PropertyFilter(Tag.TAG + "1_" + Keys.OBJECT_ID, FilterOperator.EQUAL, tag1Id));
        filters.add(new PropertyFilter(Common.WEIGHT, FilterOperator.GREATER_THAN_OR_EQUAL, WEIGHT));

        final Query query = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters)).
                setCurrentPageNum(currentPageNum).setPageSize(pageSize).setPageCount(1).
                addSort(Common.WEIGHT, SortDirection.DESCENDING);

        return get(query);
    }

    /**
     * Gets tag-tag relations by the specified tag2 id.
     *
     * @param tag2Id         the specified tag2 id
     * @param currentPageNum the specified current page number, MUST greater then {@code 0}
     * @param pageSize       the specified page size(count of a page contains objects), MUST greater then {@code 0}
     * @return for example      <pre>
     * {
     *     "pagination": {
     *       "paginationPageCount": 88250
     *     },
     *     "rslts": [{
     *         "oId": "",
     *         "tag1_oId": "",
     *         "tag2_oId": tag2Id,
     *         "weight": int
     *     }, ....]
     * }
     * </pre>
     * @throws RepositoryException repository exception
     */
    public JSONObject getByTag2Id(final String tag2Id, final int currentPageNum, final int pageSize)
            throws RepositoryException {
        final List<Filter> filters = new ArrayList<>();
        filters.add(new PropertyFilter(Tag.TAG + "2_" + Keys.OBJECT_ID, FilterOperator.EQUAL, tag2Id));
        filters.add(new PropertyFilter(Common.WEIGHT, FilterOperator.GREATER_THAN_OR_EQUAL, WEIGHT));

        final Query query = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters)).
                setCurrentPageNum(currentPageNum).setPageSize(pageSize).setPageCount(1).
                addSort(Common.WEIGHT, SortDirection.DESCENDING);

        return get(query);
    }

    /**
     * Gets a tag-tag relation by the specified tag1 id and tag2 id.
     *
     * @param tag1Id the specified tag1 id
     * @param tag2Id the specified tag2 id
     * @return for example      <pre>
     * {
     *     "oId": "",
     *     "tag1_oId": tag1Id,
     *     "tag2_oId": tag2Id,
     *     "weight": int
     * }, returns {@code null} if not found
     * </pre>
     * @throws RepositoryException repository exception
     */
    public JSONObject getByTag1IdAndTag2Id(final String tag1Id, final String tag2Id)
            throws RepositoryException {
        final List<Filter> filters = new ArrayList<>();
        filters.add(new PropertyFilter(Tag.TAG + "1_" + Keys.OBJECT_ID, FilterOperator.EQUAL, tag1Id));
        filters.add(new PropertyFilter(Tag.TAG + "2_" + Keys.OBJECT_ID, FilterOperator.EQUAL, tag2Id));

        final Query query = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));

        final JSONArray result = get(query).optJSONArray(Keys.RESULTS);
        if (result.length() < 1) {
            return null;
        }

        return result.optJSONObject(0);
    }
}
