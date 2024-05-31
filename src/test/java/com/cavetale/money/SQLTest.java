package com.cavetale.money;

import com.winthier.sql.SQLDatabase;
import org.junit.Test;

public final class SQLTest {
    @Test
    public void test() {
        System.out.println(SQLDatabase.testTableCreation(SQLLog.class));
        System.out.println(SQLDatabase.testTableCreation(SQLAccount.class));
    }
}
