package mil.dds.anet.search.mssql;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;

import jersey.repackaged.com.google.common.base.Joiner;
import mil.dds.anet.beans.Poam;
import mil.dds.anet.beans.lists.AbstractAnetBeanList.PoamList;
import mil.dds.anet.beans.search.ISearchQuery.SortOrder;
import mil.dds.anet.beans.search.PoamSearchQuery;
import mil.dds.anet.beans.search.PoamSearchQuery.PoamSearchSortBy;
import mil.dds.anet.database.mappers.PoamMapper;
import mil.dds.anet.search.IPoamSearcher;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.utils.Utils;

public class MssqlPoamSearcher implements IPoamSearcher {

	@Override
	public PoamList runSearch(PoamSearchQuery query, Handle dbHandle) {
		final List<String> whereClauses = new LinkedList<String>();
		final Map<String,Object> args = new HashMap<String,Object>();
		final StringBuilder sql = new StringBuilder("/* MssqlPoamSearch */ SELECT poams.*");

		final String text = query.getText();
		final boolean doFullTextSearch = (text != null && !text.trim().isEmpty());
		if (doFullTextSearch) {
			// If we're doing a full-text search, add a pseudo-rank (giving LIKE matches the highest possible score)
			// so we can sort on it (show the most relevant hits at the top).
			sql.append(", ISNULL(c_poams.rank, 0)"
					+ " + CASE WHEN poams.shortName LIKE :likeQuery THEN 1000 ELSE 0 END");
			sql.append(" AS search_rank");
		}
		sql.append(", COUNT(*) OVER() AS totalCount FROM poams");

		if (doFullTextSearch) {
			sql.append(" LEFT JOIN CONTAINSTABLE (poams, (longName), :containsQuery) c_poams"
					+ " ON poams.id = c_poams.[Key]");
			whereClauses.add("(c_poams.rank IS NOT NULL"
					+ " OR poams.shortName LIKE :likeQuery)");
			args.put("containsQuery", Utils.getSqlServerFullTextQuery(text));
			args.put("likeQuery", Utils.prepForLikeQuery(text) + "%");
		}

		String commonTableExpression = null;
		if (query.getResponsibleOrgId() != null) {
			if (query.getIncludeChildrenOrgs() != null && query.getIncludeChildrenOrgs()) {
				commonTableExpression = "WITH parent_orgs(id) AS ( "
						+ "SELECT id FROM organizations WHERE id = :orgId "
					+ "UNION ALL "
						+ "SELECT o.id from parent_orgs po, organizations o WHERE o.parentOrgId = po.id "
					+ ") ";
				whereClauses.add(" organizationId IN (SELECT id from parent_orgs)");
			} else {
				whereClauses.add("organizationId = :orgId");
			}
			args.put("orgId", query.getResponsibleOrgId());
		}

		if (query.getCategory() != null) {
			whereClauses.add("poams.category = :category");
			args.put("category", query.getCategory());
		}

		if (query.getStatus() != null) {
			whereClauses.add("poams.status = :status");
			args.put("status", DaoUtils.getEnumId(query.getStatus()));
		}

		final PoamList result =  new PoamList();
		result.setPageNum(query.getPageNum());
		result.setPageSize(query.getPageSize());

		if (whereClauses.isEmpty()) {
			return result;
		}

		sql.append(" WHERE ");
		sql.append(Joiner.on(" AND ").join(whereClauses));
		//Sort Ordering
		final List<String> orderByClauses = new LinkedList<>();
		if (doFullTextSearch && query.getSortBy() == null) {
			// We're doing a full-text search without an explicit sort order,
			// so sort first on the search pseudo-rank.
			orderByClauses.addAll(Utils.addOrderBy(SortOrder.DESC, null, "search_rank"));
		}

		if (query.getSortBy() == null) { query.setSortBy(PoamSearchSortBy.NAME); }
		if (query.getSortOrder() == null) { query.setSortOrder(SortOrder.ASC); }
		switch (query.getSortBy()) {
			case CREATED_AT:
				orderByClauses.addAll(Utils.addOrderBy(query.getSortOrder(), "poams", "createdAt"));
				break;
			case CATEGORY:
				orderByClauses.addAll(Utils.addOrderBy(query.getSortOrder(), "poams", "category"));
				break;
			case NAME:
			default:
				orderByClauses.addAll(Utils.addOrderBy(query.getSortOrder(), "poams", "shortName", "longName"));
				break;
		}
		orderByClauses.addAll(Utils.addOrderBy(SortOrder.ASC, "poams", "id"));
		sql.append(" ORDER BY ");
		sql.append(Joiner.on(", ").join(orderByClauses));

		if (commonTableExpression != null) {
			sql.insert(0, commonTableExpression);
		}

		final Query<Poam> sqlQuery = MssqlSearcher.addPagination(query, dbHandle, sql, args)
			.map(new PoamMapper());
		return PoamList.fromQuery(sqlQuery, query.getPageNum(), query.getPageSize());
	}

}
