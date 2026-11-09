package com.szdp.shoppinglistapi.list;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ArchivedListCleanup {
    private final ShoppingListRepository lists;

    public ArchivedListCleanup(ShoppingListRepository lists) {
        this.lists = lists;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public int purgeExpiredLists() {
        return lists.deleteArchivedBefore(Instant.now().minus(30, ChronoUnit.DAYS));
    }
}
