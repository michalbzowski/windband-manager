package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.UiTestBase;

/**
 * UI tests for Issue #121: Moving navigation buttons (Back, Edit, Delete) from the bottom to unified header/action bar on Szczegóły wydarzenia and Szczegóły spotkania pages.
 */class EventRehearsalDetailActionsBarUiTest extends UiTestBase {    @Autowired
    private JdbcTemplate jdbcTemplate;

}