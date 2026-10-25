package com.hongjie.pms.contracts.search;
public final class SearchProtocol {
    private SearchProtocol() {}
    public static final String PATH="/internal/search/v1/query";
    public static final String TOKEN_HEADER="X-PMS-Service-Token";
    public static final String INDEX_ALIAS="pms_unified_search";
}
