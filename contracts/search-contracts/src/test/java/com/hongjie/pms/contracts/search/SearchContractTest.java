package com.hongjie.pms.contracts.search;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SearchContractTest {
    @Test
    void rejectsInvalidPagingTypesAndActors() {
        assertThatThrownBy(() -> new SearchQuery("cat", 0, 20, List.of(), "time", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new SearchQuery(
                                        "cat", Integer.MAX_VALUE, 20, List.of(), "time", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchQuery("cat", 1, 20, List.of("user"), "time", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchQuery("cat", 1, 20, List.of(), "time", 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inputAndOutputListsAreImmutableSnapshots() {
        var values = new ArrayList<>(List.of("pet"));
        var query = new SearchQuery("cat", 1, 20, values, "time", 7L);
        values.clear();
        assertThat(query.types()).containsExactly("pet");
        var rows = new ArrayList<>(List.of(new SearchResult.Candidate("pet", 1L)));
        var result = new SearchResult(1, rows);
        rows.clear();
        assertThat(result.candidates()).hasSize(1);
        assertThatThrownBy(() -> new SearchResult.Candidate("pet", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
