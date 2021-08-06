/*
 * Copyright © 2020-2021, MetricStream, Inc. All rights reserved.
 */
package com.metricstream.jdbc

import com.metricstream.jdbc.MockSQLBuilderProvider.Companion.enable
import com.metricstream.jdbc.MockSQLBuilderProvider.Companion.disable
import com.metricstream.jdbc.MockSQLBuilderProvider.Companion.reset
import com.metricstream.jdbc.MockResultSet.Companion.empty
import com.metricstream.jdbc.MockResultSet.Companion.create
import com.metricstream.jdbc.MockSQLBuilderProvider.Companion.setIntByColumnIndex
import com.metricstream.jdbc.MockSQLBuilderProvider.Companion.setExecute
import com.metricstream.jdbc.MockResultSet.Companion.broken
import com.metricstream.jdbc.SQLBuilder.Companion.setDelegate
import com.metricstream.jdbc.SQLBuilder.Companion.entry
import com.metricstream.jdbc.SQLBuilder.Companion.fromNumberedParameters
import com.metricstream.jdbc.SQLBuilder.Companion.mask
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.Throws
import java.sql.SQLException
import java.sql.ResultSet
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.Clock
import java.util.Arrays
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.optional.shouldNotBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.mockito.Mockito
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance

@ExperimentalStdlibApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SQLBuilderTest {
    private val sqlBuilder = SQLBuilder("SELECT 42 FROM DUAL")

    private val mockConnection = Mockito.mock(Connection::class.java)

    @BeforeAll
    fun beforeAll() {
        enable()
    }

    @AfterAll
    fun afterAll() {
        disable()
    }

    @AfterEach
    fun afterEach() {
        reset()
    }

    @Test
    @Throws(SQLException::class)
    fun testMock() {
        val mrs: ResultSet = create("testMock:sb1", arrayOf("name", "age"), arrayOf(arrayOf("Alice", 20), arrayOf("Bob", 35), arrayOf("Charles", 50)))
        MockSQLBuilderProvider.addResultSet(mrs)
        MockSQLBuilderProvider.addResultSet(create("testMock:sb2", arrayOf("key", "value"), arrayOf()))
        MockSQLBuilderProvider.addResultSet(empty("testMock:sb3"))
        MockSQLBuilderProvider.addResultSet(create("testMock:sb4", arrayOf(arrayOf("a"), arrayOf("b"))))
        val sb1 = SQLBuilder("select name, age from friends where age > 18")
        sb1.getResultSet(mockConnection).use { rs ->
            var total = 0
            while (rs.next()) {
                total += rs.getInt(2)
            }
            total shouldBe 105
        }
        val sb2 = SQLBuilder("select value from lookup where key=?", "first")
        sb2.getResultSet(mockConnection).use { rs ->
            var value: String? = null
            if (rs.next()) {
                value = rs.getString("value")
            }
            value shouldBe null
        }
        val sb3 = SQLBuilder("select value from lookup where key=?", "second")
        sb3.getResultSet(mockConnection).use { rs ->
            var value: String? = null
            if (rs.next()) {
                value = rs.getString("value")
            }
            value shouldBe null
        }
        val sb4 = SQLBuilder("select value from \${table}").bind("table", "VN")
        sb4.getResultSet(mockConnection).use { rs ->
            if (rs.next()) {
                rs.getString(1) shouldBe "a"
            }
            if (rs.next()) {
                rs.getString("COLUMN1") shouldBe "b"
            }
            rs.next() shouldBe false
        }
        val sb5 = SQLBuilder("select count(*) from lookup")
        setIntByColumnIndex { idx: Int?, def: Int? -> 10 }
        sb5.getInt(mockConnection, 1, 0) shouldBe 10
        val sb6 = SQLBuilder("select count(*) from lookup")
        sb6.getInt(mockConnection, 1, 0) shouldBe 10
        MockSQLBuilderProvider.addResultSet(create("testMock:sb7", arrayOf(arrayOf("a"), arrayOf("b"))))
        val sb7 = SQLBuilder("select value from lookup where key = ?", 42)
        sb7.getString(mockConnection, 1, "default") shouldBe "a"
        MockSQLBuilderProvider.addResultSet("testMock:sb8", "Alice,20\nBob,35\nCharles,50")
        val sb8 = SQLBuilder("select name, age from friends where age > 18")
        sb8.getResultSet(mockConnection).use { rs ->
            var total = 0
            while (rs.next()) {
                total += rs.getInt(2)
            }
            total shouldBe 105
        }
        MockSQLBuilderProvider.addResultSet("testMock:sb9", "name,age", "Alice,20\nBob,35\nCharles,50")
        val sb9 = SQLBuilder("select name, age from friends where age > 18")
        sb9.getResultSet(mockConnection).use { rs ->
            var total: Long = 0
            while (rs.next()) {
                total += rs.getLong("age")
            }
            total shouldBe 105L
        }
        MockSQLBuilderProvider.addResultSet(
            "testMock:sb10",
            "name,age",
            "Alice,20",
            "Bob,35",
            "Charles,50"
        )
        val sb10 = SQLBuilder("select age, name from friends where age > 18")
        sb10.getResultSet(mockConnection).use { rs ->
            var total: Long = 0
            while (rs.next()) {
                total += rs.getLong("AGE")
            }
            total shouldBe 105L
        }
        MockSQLBuilderProvider.addResultSet("testMock:read from CSV file", javaClass.getResourceAsStream("sb11.csv")!!)
        val sb11 = SQLBuilder("select USER_ID, FIRST_NAME, LAST_NAME, DEPARTMENT from si_users_t")
        sb11.getList(mockConnection, { rs -> rs.getLong("USER_ID") }).toString() shouldBe "[100000, 100001, 100002, 100003]"

        // SI_USERS_T.csv was produced via SQLDeveloper using "Export as csv" from right-click on the table
        MockSQLBuilderProvider.addResultSet("testMock:read from sqldeveloper export file", javaClass.getResourceAsStream("SI_USERS_T.csv")!!)
        val sb12 = SQLBuilder("select USER_ID, FIRST_NAME, LAST_NAME, DEPARTMENT from si_users_t")
        sb12.getList(mockConnection, { it.getLong("USER_ID") }).toString() shouldBe "[100000, 100001, 100002, 100003]"
        val ts = Timestamp.from(Instant.now())
        MockSQLBuilderProvider.addResultSet(create("testMock:sb13", arrayOf(arrayOf(ts))))
        val sb13 = SQLBuilder("select value from lookup where key = ?", 42)
        sb13.getTimestamp(mockConnection, 1, null) shouldBe ts
        MockSQLBuilderProvider.addResultSet(create("testMock:sb14", arrayOf(arrayOf(ts))))
        val sb14 = SQLBuilder("select value from lookup where key = ?", 42)
        sb14.getResultSet(mockConnection).use { rs14 ->
            var ts14: Timestamp? = null
            if (rs14.next()) {
                ts14 = rs14.getTimestamp(1)
            }
            ts14 shouldBe ts
        }
        MockSQLBuilderProvider.addResultSet(create("testMock:sb15", arrayOf("value"), arrayOf(arrayOf(ts))))
        val sb15 = SQLBuilder("select value from lookup where key = ?", 42)
        sb15.getResultSet(mockConnection).use { rs15 ->
            var ts15: Timestamp? = null
            if (rs15.next()) {
                ts15 = rs15.getTimestamp("value")
            }
            ts15 shouldBe ts
        }
        val date = Date(Instant.now().toEpochMilli())
        MockSQLBuilderProvider.addResultSet(create("testMock:sb16", arrayOf(arrayOf(date))))
        val sb16 = SQLBuilder("select value from lookup where key = ?", 42)
        sb16.getDate(mockConnection, 1, null) shouldBe date
        val updateFoo = SQLBuilder("update foo")
        updateFoo.execute(mockConnection) shouldBe 42

        MockSQLBuilderProvider.setExecute("testMock", 1)
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 1

        MockSQLBuilderProvider.setExecute("", 1, 0, 1)
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 0
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 42

        val count = AtomicInteger()
        MockSQLBuilderProvider.setExecute("testMock") { if (count.getAndIncrement() < 3) 1 else 2 }
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 1
        updateFoo.execute(mockConnection) shouldBe 2
        updateFoo.execute(mockConnection) shouldBe 2

        shouldThrow<IllegalStateException> {
            MockSQLBuilderProvider.setExecute("abc", 1)
            updateFoo.execute(mockConnection) shouldBe 1
        } shouldHaveMessage "Trying to use abc for method testMock"
    }

    @Test
    @Throws(SQLException::class)
    fun copyTest1() {
        // A resultset is consumed by a SQLBuilder `getResultSet` (or higher level callers like `getInt`). Therefore,
        // adding it once but trying to use it twice will not work.  Instead, the next usage will create a new
        // default mocked resultset
        val rs = create("copyTest1", "A", "3")
        MockSQLBuilderProvider.addResultSet(rs)
        sqlBuilder.getInt(mockConnection, 1, -1) shouldBe 3
        sqlBuilder.getInt(mockConnection, 1, -1) shouldBe 42
    }

    @Test
    @Throws(SQLException::class)
    fun copyTest2() {
        // A resultset has an internal state which keeps track of the consumed rows.  Therefore, adding the same
        // resultset twice will not produce the same result.
        val rs = create("copyTest2", "A", "3")
        MockSQLBuilderProvider.addResultSet(rs)
        sqlBuilder.getInt(mockConnection, 1, -1) shouldBe 3
        MockSQLBuilderProvider.addResultSet(rs)
        sqlBuilder.getInt(mockConnection, 1, -1) shouldBe -1
    }

    @Test
    @Throws(SQLException::class)
    fun brokenTest() {
        MockSQLBuilderProvider.addResultSet(broken("brokenTest"))
        shouldThrow<SQLException> { SQLBuilder("select A from T").getInt(mockConnection, 1, -1) }
    }

    @Test
    @Throws(SQLException::class)
    fun executeReturningTest() {
        MockSQLBuilderProvider.addResultSet("executeReturningTest:id", "43")
        val sb = SQLBuilder("insert into foo(foo_s.nextval, ?", "fooValue")
        val rs = sb.execute(mockConnection, "id")
        rs.next() shouldBe true
        rs.getInt(1) shouldBe 43
    }

    @Test
    @Throws(SQLException::class)
    fun unusedMockResultSet() {
        MockSQLBuilderProvider.addResultSet("unusedMockResultSet:first", "1")
        MockSQLBuilderProvider.addResultSet("unusedMockResultSet:second", "2")
        val sb1 = SQLBuilder("select count(*) from foo")
        sb1.getInt(mockConnection, 1, 0) shouldBe 1
    }

    @Test
    @Throws(SQLException::class)
    fun testDateTime() {
        val now = OffsetDateTime.now()
        MockSQLBuilderProvider.addResultSet(create("testDateTime", arrayOf(arrayOf(now))))
        val sb1 = SQLBuilder("select created from lookup where key = ?", 42)
        sb1.getDateTime(mockConnection, 1, null) shouldBe now
        val sb2 = SQLBuilder("select created from lookup where key = ?", 42)
        val dt2 = sb2.getDateTime(mockConnection, 1, null)
        dt2 shouldNotBe null
        // This will fail in about 3000 years.  Hopefully, mankind is still around by then but no longer uses JDBC...
        dt2!!.isAfter(now.plusYears(1000L)) shouldBe true
        val sb3 = SQLBuilder("select created from lookup where key = ?", 42)
        val dt3 = sb3.getDateTime(mockConnection, "created", null)
        dt3 shouldNotBe null
        // This will fail in about 3000 years.  Hopefully, mankind is still around by then but no longer uses JDBC...
        dt3!!.isAfter(now.plusYears(1000L)) shouldBe true
    }

    @Test
    @Throws(SQLException::class)
    fun testInstant() {
        val now = Clock.systemUTC().instant()
        val oNow = now.atOffset(ZoneOffset.UTC)
        MockSQLBuilderProvider.addResultSet(create("testInstant", arrayOf(arrayOf(oNow))))
        val sb1 = SQLBuilder("select created from lookup where key = ?", 42)
        sb1.getInstant(mockConnection, 1, null) shouldBe now
        val sb2 = SQLBuilder("select created from lookup where key = ?", 42)
        val dt2 = sb2.getInstant(mockConnection, 1, null)
        dt2 shouldNotBe null
        // This will fail in about 1150 years.  Hopefully, mankind is still around by then but no longer uses JDBC...
        dt2!!.isAfter(now.plus(420000L, ChronoUnit.DAYS)) shouldBe true
        val sb3 = SQLBuilder("select created from lookup where key = ?", 42)
        val dt3 = sb3.getInstant(mockConnection, "created", null)
        dt3 shouldNotBe null
        // This will fail in about 1150 years.  Hopefully, mankind is still around by then but no longer uses JDBC...
        dt3!!.isAfter(now.plus(420000L, ChronoUnit.DAYS)) shouldBe true
    }

    @Test
    @Throws(SQLException::class)
    fun yesForever() {
        setDelegate(MockSQLBuilderProvider(true, true))
        val sb1 = SQLBuilder("select count(*) from lookup")
        sb1.getResultSet(mockConnection).use { rs ->
            rs.next() shouldBe true
            rs.next() shouldBe false
        }
        sb1.getResultSet(mockConnection).use { rs ->
            rs.next() shouldBe true
            rs.next() shouldBe false
        }
    }

    @Test
    @Throws(SQLException::class)
    fun noForever() {
        try {
            setDelegate(MockSQLBuilderProvider(false, true))
            val sb1 = SQLBuilder("select count(*) from lookup")
            sb1.getResultSet(mockConnection).use { rs -> rs.next() shouldBe false }
            sb1.getResultSet(mockConnection).use { rs -> rs.next() shouldBe false }
        } finally {
            setDelegate(MockSQLBuilderProvider(true, true))
        }
    }

    @Test
    @Throws(SQLException::class)
    fun emptyForever() {
        setDelegate(MockSQLBuilderProvider())
        MockSQLBuilderProvider.addResultSet(empty(""))
        val sb = SQLBuilder("select count(*) from lookup")
        sb.getResultSet(mockConnection).use { rs -> rs.next() shouldBe false }
        sb.getResultSet(mockConnection).use { rs ->
            rs.next() shouldBe true
            rs.next() shouldBe false
        }
    }

    @Test
    fun list_test1() {
        // This is not so nice: `getList` only really works with resultsets and not with provider functions because these do not
        // participate in the `while (rs.next())` looping around the RowMapper that `getList` uses.  What could be nicer is a
        // projection (e.g. `addResultColumn(2, new int[] { 3, 1, 4})` or a limited provider function what affects `rs.next()`.
        // But then something like `sb.getList(conn, rs -> new Foo(rs.getLong(1), rs.getString(2)));` still would not work. So
        // perhaps this whole "provider functions" idea is bogus...

        // when query returns 3 rows
        // with 3 ints in column 2
        // then expect to get a list with 3 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", "_,3\n_,1\n_,4")
        val actual = sqlBuilder.getList(mockConnection, { rs -> rs.getInt(2) })
        actual shouldBe listOf(3, 1, 4)
    }

    @Test
    fun list_test2() {
        // when query returns 3 rows
        // with 2 ints and a null in column 2
        // then expect to get a list with 3 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("", 3), arrayOf("", null), arrayOf("", 4)))
        val actual = sqlBuilder.getList(mockConnection, { rs: ResultSet -> rs.getInt(2) })
        actual shouldBe listOf(3, 0, 4)
    }

    @Test
    fun list_test4() {
        // when query returns 3 rows
        // with 2 Strings and a null in column 2
        // then expect to get a list with 2 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("", "first"), arrayOf("", null), arrayOf("", "third")))
        val actual = sqlBuilder.getList(mockConnection, { rs: ResultSet -> rs.getString(2) })
        actual shouldBe listOf("first", "third")
    }

    @Test
    fun list_test5() {
        // when query returns 3 rows
        // with 2 Strings and a null in column 2
        // then expect to get a list with 3 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("", "first"), arrayOf("", null), arrayOf("", "third")))
        val actual = sqlBuilder.getList(mockConnection, { rs: ResultSet -> rs.getString(2) }, true)
        actual shouldBe listOf("first", null, "third")
    }

    @Test
    fun list_test6() {
        // when query returns 3 rows
        // with 2 ints and a null (converted to 0 by getInt()) in column 2
        // then expect to get a list with 3 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("", 1), arrayOf("", null), arrayOf("", 3)))
        val actual = sqlBuilder.getList(mockConnection, { rs: ResultSet ->
            val i = rs.getInt(2)
            if (rs.wasNull()) -1 else i
        })
        actual shouldBe listOf(1, -1, 3)
    }

    @Test
    fun list_test7() {
        // when query returns 3 rows
        // with 2 ints and a null (converted to 0 by getInt()) in column 2
        // then expect to get a list with 2 elements in the correct order.  We must avoid
        // calling `getInt` here because that automatically converts null to 0.
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("", 1), arrayOf("", null), arrayOf("", 3)))
        val actual = sqlBuilder.getList<Any?>(mockConnection, { rs -> rs.getObject(2) }).map { it as Int }
        actual shouldBe listOf(1, 3)
    }

    @Test
    fun list_test8() {
        // when query returns 3 rows
        // with 2 ints and a null in column 2
        // then expect to get 3 elements with null mapped to null
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("", 1), arrayOf("", null), arrayOf("", 3)))
        val actual = sqlBuilder.getList<Any?>(mockConnection, { rs -> rs.getObject(2) }, true).map { it as Int? }
        actual shouldBe listOf(1, null, 3)
    }

    @Test
    fun map_test1() {
        // when query returns 3 rows
        // with 3 ints in column 2
        // then expect to get a list with 3 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", "3,Three\n1,One\n4,Four")
        val m = sqlBuilder.getMap(mockConnection, { rs: ResultSet -> entry(rs.getInt(1), rs.getString(2)) })
        m.keys.shouldContainExactlyInAnyOrder(3, 1, 4)
        m.containsValue("Three") shouldBe true
    }

    @Test
    fun map_testDuplicateKeys() {
        // when query returns 3 rows with duplicate keys
        // then expect to get an IllegalStateException
        MockSQLBuilderProvider.addResultSet("", "3,Three\n1,One\n3,Four")
        shouldThrow<IllegalStateException> {
            sqlBuilder.getMap(mockConnection, { rs: ResultSet -> entry(rs.getInt(1), rs.getString(2)) })
        }
    }

    @Test
    fun map_testNullKey() {
        // when query returns rows with null keys
        // then expect to get an IllegalStateException
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf(3, "Three"), arrayOf(null, "Zero")))
        // Note: we cannot use `getInt` for the key here because that would automatically convert `null` to `0` and thus not throw the expected exception
        val exp = shouldThrow<IllegalStateException> {
            sqlBuilder.getMap(mockConnection, { rs: ResultSet -> entry(rs.getObject(1), rs.getString(2)) })
        }
        exp.message shouldContain "unsupported"
    }

    @Test
    fun map_test3() {
        // when query returns 3 rows
        // with 3 ints in column 2
        // then expect to get a list with 3 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("1", 1), arrayOf("2", null), arrayOf("3", 3)))
        val m: Map<String, Int?> = sqlBuilder.getMap(mockConnection, { rs: ResultSet -> entry(rs.getString(1), rs.getInt(2)) })
        // size is 3 and not 2 although 2 is mapped to null because we use getInt which will automatically convert null to 0
        m.keys.shouldContainExactlyInAnyOrder("1", "2", "3")
    }

    @Test
    fun map_test4() {
        // when query returns 3 rows
        // with 3 ints in column 2
        // then expect to get a list with 3 elements in the correct order
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("1", 1), arrayOf("2", null), arrayOf("3", 3)))
        val m = sqlBuilder.getMap(mockConnection, { rs: ResultSet -> entry(rs.getString(1), rs.getInt(2)) }, false)
        m.keys.shouldContainExactlyInAnyOrder("1", "2", "3")
    }

    @Test
    fun single_test1() {
        // when query returns 3 rows
        // with 3 longs in column 1
        // then expect to get the first element
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf(3L), arrayOf(1L), arrayOf(4L)))
        val actual = sqlBuilder.getSingle(mockConnection) { rs: ResultSet -> rs.getLong(1) }
        actual shouldBePresent { it shouldBe 3L }
    }

    @Test
    fun string_test1() {
        // when query returns 3 rows
        // with 3 Strings in column 1
        // then expect to get the first element
        MockSQLBuilderProvider.addResultSet("", "first\nsecond\nthird")
        val s = sqlBuilder.getString(mockConnection, 1, "default")
        s shouldBe "first"
    }

    @Test
    fun string_test2() {
        // when query returns 1 row
        // with 3 Strings in column 1
        // then expect to get the first element
        MockSQLBuilderProvider.addResultSet("", "first")
        val s = sqlBuilder.getString(mockConnection, 1, "default")
        s shouldBe "first"
    }

    @Test
    fun string_test3() {
        // when query returns no rows
        // then expect to get the default element
        MockSQLBuilderProvider.addResultSet(empty(""))
        val s = sqlBuilder.getString(mockConnection, 1, "default")
        s shouldBe "default"
    }

    @Test
    fun string_test4() {
        // when query returns no rows
        // then expect to get the default element even if that is null
        MockSQLBuilderProvider.addResultSet(empty(""))
        val s = sqlBuilder.getString(mockConnection, 1, null)
        s shouldBe null
    }

    @Test
    fun string_test5() {
        // when query returns 3 rows
        // with 3 Strings in column "LABEL"
        // then expect to get the first element
        MockSQLBuilderProvider.addResultSet("", "LABEL", "first\nsecond\nthird")
        val s = sqlBuilder.getString(mockConnection, "LABEL", "default")
        s shouldBe "first"
    }

    @Test
    fun single_test2() {
        // when query returns no rows
        // then expect to get an empty optional
        MockSQLBuilderProvider.addResultSet(MockResultSet.empty(""))
        val actual = sqlBuilder.getSingle(mockConnection) { rs: ResultSet -> rs.getLong(1) }
        actual.shouldNotBePresent()
    }

    @Test
    fun resultSet_test1() {
        // when query returns 3 rows
        // with 3 ints in column 2
        // then expect to get a resultset that returns 3 rows in correct order
        MockSQLBuilderProvider.addResultSet("", "_,3\n_,1\n_,4")
        val rs = sqlBuilder.getResultSet(mockConnection)
        val actual = mutableListOf<Int>()
        while (rs.next()) {
            actual.add(rs.getInt(2))
        }
        actual shouldBe listOf(3, 1, 4)
    }

    @Test
    fun resultSet_test2() {
        // when query returns 1 row
        // with 1 long in column 3
        // then expect to get a resultset that returns 1 row
        MockSQLBuilderProvider.addResultSet("", arrayOf(arrayOf("", "", 3L)))
        val rs = sqlBuilder.getResultSet(mockConnection)
        val actual = mutableListOf<Long>()
        while (rs.next()) {
            actual.add(rs.getLong(3))
        }
        actual shouldBe listOf(3L)
    }

    @Test
    fun resultSet_test3() {
        // when query returns no rows
        // then expect to get a resultset that returns no row
        MockSQLBuilderProvider.addResultSet(MockResultSet.empty(""))
        val rs = sqlBuilder.getResultSet(mockConnection)
        rs.next() shouldBe false
    }

    @Test
    fun placeholder_dollar() {
        val sb = SQLBuilder("select a, \${b} from \${t} where x > ?", 5)
        sb.toString() shouldBe "select a, \${b} from \${t} where x > ?; args=[5]"
        sb.bind("b", "BCOL").bind("t", "table1")
        sb.toString() shouldBe "select a, BCOL from table1 where x > ?; args=[5]"
    }

    @Test
    fun placeholder_colon() {
        val sb = SQLBuilder("select a, :{b} from :{t} where x > ?", 5)
        sb.toString() shouldBe "select a, :{b} from :{t} where x > ?; args=[5]"
        sb.bind("b", "BCOL").bind("t", "table1")
        sb.toString() shouldBe "select a, BCOL from table1 where x > ?; args=[5]"
    }

    @Test
    fun multiPlaceholder_dollar() {
        val sb = SQLBuilder("select a, \${b} from \${t} where x > ?", 5)
        sb.toString() shouldBe "select a, \${b} from \${t} where x > ?; args=[5]"
        sb.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
    }

    @Test
    fun multiPlaceholder_colon() {
        val sb = SQLBuilder("select a, :{b} from :{t} where x > ?", 5)
        sb.toString() shouldBe "select a, :{b} from :{t} where x > ?; args=[5]"
        sb.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
    }

    @Test
    fun invalidPlaceholder_dollar() {
        val sb = SQLBuilder("select a, \${b+} from \${t} where x > ?", 5)
        sb.toString() shouldBe "select a, \${b+} from \${t} where x > ?; args=[5]"
        shouldThrow<IllegalArgumentException> { sb.bind("b+", "BCOL") }
    }

    @Test
    fun invalidPlaceholder_colon() {
        val sb = SQLBuilder("select a, :{b+} from :{t} where x > ?", 5)
        sb.toString() shouldBe "select a, :{b+} from :{t} where x > ?; args=[5]"
        shouldThrow<IllegalArgumentException> { sb.bind("b+", "BCOL") }
    }

    @Test
    fun repeatedPlaceholder() {
        shouldThrow<IllegalArgumentException> { SQLBuilder("").bind("a", "first").bind("a", "second") }
    }

    @Test
    fun multiBuilder_dollar() {
        val sb1 = SQLBuilder("select a, \${b} from \${t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        val sb2 = SQLBuilder(sb1)
        sb2.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
    }

    @Test
    fun multiBuilder_colon() {
        val sb1 = SQLBuilder("select a, :{b} from :{t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        val sb2 = SQLBuilder(sb1)
        sb2.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
    }

    @Test
    fun repeatedPlaceholder2() {
        val sb1 = SQLBuilder("").bind("a", "first")
        val sb2 = SQLBuilder("").bind("a", "first")
        shouldThrow<IllegalArgumentException> { sb1.append(sb2) }
    }

    @Test
    fun repeatedPlaceholder3_dollar() {
        val sb1 = SQLBuilder("select a, \${b} from \${t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        val sb2 = SQLBuilder("select \${b} from (").append(sb1).append(")")
        sb2.toString() shouldBe "select BCOL, CCOL from ( select a, BCOL, CCOL from table1 where x > ? ); args=[5]"
    }

    @Test
    fun repeatedPlaceholder3_colon() {
        val sb1 = SQLBuilder("select a, :{b} from :{t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        val sb2 = SQLBuilder("select :{b} from (").append(sb1).append(")")
        sb2.toString() shouldBe "select BCOL, CCOL from ( select a, BCOL, CCOL from table1 where x > ? ); args=[5]"
    }

    @Test
    fun repeatedPlaceholder4_dollar() {
        val sb1 = SQLBuilder("select a, \${b} from \${t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        val sb2 = SQLBuilder("select \${b} from (").append(sb1).append(")")
        shouldThrow<IllegalArgumentException> { sb2.bind("b", "BCOL") }
    }

    @Test
    fun repeatedPlaceholder4_colon() {
        val sb1 = SQLBuilder("select a, :{b} from :{t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        val sb2 = SQLBuilder("select :{b} from (").append(sb1).append(")")
        shouldThrow<IllegalArgumentException> { sb2.bind("b", "BCOL") }
    }

    @Test
    fun repeatedPlaceholder5_dollar() {
        val sb1 = SQLBuilder("select a, \${b} from \${t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        val sb2 = SQLBuilder("select \${b} from (").append(sb1.applyBindings()).append(")").bind("b", "BCOL")
        sb2.toString() shouldBe "select BCOL from ( select a, BCOL, CCOL from table1 where x > ? ); args=[5]"
    }

    @Test
    fun repeatedPlaceholder5_colon() {
        val sb1 = SQLBuilder("select a, :{b} from :{t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        val sb2 = SQLBuilder("select :{b} from (").append(sb1.applyBindings()).append(")").bind("b", "BCOL")
        sb2.toString() shouldBe "select BCOL from ( select a, BCOL, CCOL from table1 where x > ? ); args=[5]"
    }

    @Test
    fun repeatedPlaceholder6_dollar() {
        val sb1 = SQLBuilder("select a, \${b} from \${t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        val sb1s = sb1.toString()
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        sb1.applyBindings()
        sb1.toString() shouldBe sb1s
        val sb2 = SQLBuilder("select \${b} from (").append(sb1).append(")").bind("b", "BCOL")
        sb2.toString() shouldBe "select BCOL from ( select a, BCOL, CCOL from table1 where x > ? ); args=[5]"
    }

    @Test
    fun repeatedPlaceholder6_colon() {
        val sb1 = SQLBuilder("select a, :{b} from :{t} where x > ?", 5)
        sb1.bind("b", listOf("BCOL", "CCOL")).bind("t", "table1")
        val sb1s = sb1.toString()
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        sb1.toString() shouldBe "select a, BCOL, CCOL from table1 where x > ?; args=[5]"
        sb1.applyBindings()
        sb1.toString() shouldBe sb1s
        val sb2 = SQLBuilder("select :{b} from (").append(sb1).append(")").bind("b", "BCOL")
        sb2.toString() shouldBe "select BCOL from ( select a, BCOL, CCOL from table1 where x > ? ); args=[5]"
    }

    @Test
    fun testFromNumberedParams() {
        val params: QueryParams = QueryParamsImpl()
        fromNumberedParameters("select n from t where i=:1)", params).toString() shouldBe "select n from t where i=?); args=[a]"
        fromNumberedParameters("select n from t where i=:1 or i=:2)", params).toString() shouldBe "select n from t where i=? or i=?); args=[a, b]"
        fromNumberedParameters("select n from t where i=:2 or i=:1)", params).toString() shouldBe "select n from t where i=? or i=?); args=[b, a]"
        fromNumberedParameters("select n from t where i=:2 or k=:2)", params).toString() shouldBe "select n from t where i=? or k=?); args=[b, b]"
        fromNumberedParameters("select n from t where i=:2 or k=':4')", params).toString() shouldBe "select n from t where i=? or k=':4'); args=[b]"
        fromNumberedParameters("select n from t where i=:2 or k=':2')", params).toString() shouldBe "select n from t where i=? or k=':2'); args=[b]"
    }

    @Test
    fun maskData() {
        SQLBuilder("select name from user where secret=?", mask("oops!")).toString() shouldBe "select name from user where secret=?; args=[__masked__:982c0381c279d139fd221fce974916e7]"
    }

    @Test
    fun maskDataNull() {
        SQLBuilder("select name from user where secret=?", mask(null)).toString() shouldBe "select name from user where secret=?; args=[null]"
    }

    @Test
    fun maskDataEmpty() {
        SQLBuilder("select name from user where secret=?", mask("")).toString() shouldBe "select name from user where secret=?; args=[]"
    }

    @Test
    fun maskDataLong() {
        SQLBuilder("select name from user where secret=?", mask(42L)).toString() shouldBe "select name from user where secret=?; args=[__masked__:a1d0c6e83f027327d8461063f4ac58a6]"
    }

    @Test
    fun maskDataMixed() {
        SQLBuilder("select name from user where secret=? and public=?", mask("oops!"), "ok").toString() shouldBe "select name from user where secret=? and public=?; args=[__masked__:982c0381c279d139fd221fce974916e7, ok]"
    }

    private fun masked(value: Any): String {
        return SQLBuilder("?", mask(value)).toString()
    }

    @Test
    fun maskSame() {
        masked("hello") shouldBe masked("hello")
        masked("42".toLong()) shouldBe masked(42L)
        masked("42".toInt()) shouldBe masked(42)
    }

    @Test
    @Throws(SQLException::class)
    fun mockInt() {
        val sb = SQLBuilder("select count(*) from foo")
        setIntByColumnIndex { c: Int, d: Int ->
            when (c) {
                1 -> return@setIntByColumnIndex 3
                else -> return@setIntByColumnIndex d
            }
        }
        sb.getInt(mockConnection, 1, 4) shouldBe 3
    }

    @Test
    @Throws(SQLException::class)
    fun maxRows() {
        MockSQLBuilderProvider.addResultSet("", "_,3\n_,1\n_,4")
        // TODO: this just tests that `withMaxRows` is accepted, but not the actual implementation.
        val actual = sqlBuilder.withMaxRows(1).getList(mockConnection, { rs: ResultSet -> rs.getInt(2) })
        actual shouldBe listOf(3, 1, 4)
    }

    internal class QueryParamsImpl : QueryParams {
        // some arbitrary param values for testing
        private val values = arrayOf("a", "b", "c")
        override val paramNames = (1..values.size).map(Int::toString)

        override fun getParameterValue(name: String?): Any? {
            return getParameterValue(name, false)
        }

        override fun getParameterValue(name: String?, isMulti: Boolean): Any? {
            val value = values[name!!.toInt() - 1]
            if (isMulti) {
                val values = arrayOfNulls<String>(4)
                Arrays.fill(values, value)
                return listOf(*values)
            }
            return value
        }

        override fun getParameterValue(name: String?, isMulti: Boolean, dateAsString: Boolean): Any? {
            return getParameterValue(name, isMulti)
        }

        override fun dateAsStringNeeded(subStr: String?): Boolean {
            return false
        }

        override val dateParameterAsString: String?
            get() = null
    }
}
