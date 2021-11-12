package com.metricstream.jdbc

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.math.BigDecimal
import java.net.URL
import java.sql.Array as SQLArray
import java.sql.Blob
import java.sql.Clob
import java.sql.Date
import java.sql.NClob
import java.sql.Ref
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.RowId
import java.sql.SQLException
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Statement
import java.sql.Time
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import com.opencsv.CSVReader
import com.opencsv.exceptions.CsvException
import org.slf4j.LoggerFactory

class MockResultSet private constructor(
    tag: String,
    names: Array<String>?,
    private val data: Array<Array<Any?>>,
    usages: Int = 1
) : ResultSet {
    private val tag: String
    private val columnIndices: Map<String, Int>
    private var rowIndex = -1
    private var wasNull = false
    private var generated = false
    private var remaining = usages - 1

    private fun index(columnName: String) = columnIndices[columnName.uppercase()] ?: Int.MAX_VALUE

    private fun outOfRange(columnIndex: Int) = generated || (rowIndex >= data.size || columnIndex >= data[rowIndex].size)

    private fun answerObject(columnIndex: Int): Any? = when {
        outOfRange(columnIndex) -> THE_ANSWER_TO_THE_ULTIMATE_QUESTION.toString()
        else -> data[rowIndex][columnIndex]
    }.also {
        MockSQLBuilderProvider.invocations.getRsObject++
        wasNull = it == null
    }

    private fun answerDouble(columnIndex: Int) = when {
        outOfRange(columnIndex) -> THE_ANSWER_TO_THE_ULTIMATE_QUESTION.toDouble()
        else -> when (val value = data[rowIndex][columnIndex]) {
            is Double? -> value
            is String -> value.toDouble()
            else -> throw SQLException()
        }
    }.also {
        MockSQLBuilderProvider.invocations.getRsDouble++
        wasNull = it == null
    } ?: 0.0

    private fun answerTimestamp(columnIndex: Int) = when {
        outOfRange(columnIndex) -> Timestamp(THE_ANSWER_TO_THE_ULTIMATE_QUESTION.toLong())
        else -> when (val value = data[rowIndex][columnIndex]) {
            is Timestamp? -> value
            is String -> Timestamp.valueOf(value)
            is Long -> Timestamp(value)
            else -> throw SQLException()
        }
    }.also {
        MockSQLBuilderProvider.invocations.getRsTimestamp++
        wasNull = it == null
    }

    private fun answerDate(columnIndex: Int) = when {
        outOfRange(columnIndex) -> Date(THE_ANSWER_TO_THE_ULTIMATE_QUESTION.toLong())
        else -> when (val value = data[rowIndex][columnIndex]) {
            is Date? -> value
            is String -> Date.valueOf(value)
            is Long -> Date(value)
            else -> throw SQLException()
        }
    }.also {
        MockSQLBuilderProvider.invocations.getRsDate++
        wasNull = it == null
    }

    private fun answerOffsetDateTime(columnIndex: Int) = when {
        outOfRange(columnIndex) -> OffsetDateTime.of(4242, 4, 2, 4, 2, 4, 2, ZoneOffset.UTC)
        else -> when (val value = data[rowIndex][columnIndex]) {
            is OffsetDateTime? -> value
            is String -> OffsetDateTime.parse(value)
            else -> throw SQLException()
        }
    }.also {
        MockSQLBuilderProvider.invocations.getRsObject++
        wasNull = it == null
    }

    private fun answerLong(columnIndex: Int) = when {
        outOfRange(columnIndex) -> THE_ANSWER_TO_THE_ULTIMATE_QUESTION.toLong()
        else -> when (val value = data[rowIndex][columnIndex]) {
            is Long? -> value
            is String -> value.toLong()
            else -> throw SQLException()
        }
    }.also {
        MockSQLBuilderProvider.invocations.getRsLong++
        wasNull = it == null
    } ?: 0L

    private fun answerInt(columnIndex: Int) = when {
        outOfRange(columnIndex) -> THE_ANSWER_TO_THE_ULTIMATE_QUESTION
        else -> when (val value = data[rowIndex][columnIndex]) {
            is Int? -> value
            is String -> value.toInt()
            else -> throw SQLException()
        }
    }.also {
        MockSQLBuilderProvider.invocations.getRsInt++
        wasNull = it == null
    } ?: 0

    private fun answerString(columnIndex: Int) = when {
        outOfRange(columnIndex) -> THE_ANSWER_TO_THE_ULTIMATE_QUESTION.toString()
        else -> data[rowIndex][columnIndex] as String?
    }.also {
        MockSQLBuilderProvider.invocations.getRsString++
        wasNull = it == null
    }

    private fun answerBigDecimal(columnIndex: Int) = when {
        outOfRange(columnIndex) -> THE_ANSWER_TO_THE_ULTIMATE_QUESTION.toBigDecimal()
        else -> data[rowIndex][columnIndex] as BigDecimal?
    }.also {
        MockSQLBuilderProvider.invocations.getRsBigDecimal++
        wasNull = it == null
    }

    override fun <T : Any?> unwrap(p0: Class<T>?): T {
        TODO("Not yet implemented")
    }

    override fun isWrapperFor(p0: Class<*>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
    }

    override fun next(): Boolean {
        MockSQLBuilderProvider.invocations.next++
        if (remaining < -1) {
            throw SQLException("Forced exception")
        }
        rowIndex++
        if (rowIndex == data.size && remaining > 0) {
            rowIndex = 0
            remaining--
        }
        return rowIndex < data.size
    }

    override fun wasNull(): Boolean {
        return wasNull
    }

    override fun getString(columnIndex: Int): String? = answerString(columnIndex - 1)

    override fun getString(columnLabel: String): String? = answerString(index(columnLabel))

    override fun getBoolean(columnIndex: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun getBoolean(columnLabel: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getByte(columnIndex: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun getByte(columnLabel: String): Byte {
        TODO("Not yet implemented")
    }

    override fun getShort(columnIndex: Int): Short {
        TODO("Not yet implemented")
    }

    override fun getShort(columnLabel: String): Short {
        TODO("Not yet implemented")
    }

    override fun getInt(columnIndex: Int): Int = answerInt(columnIndex -1)

    override fun getInt(columnLabel: String): Int = answerInt(index(columnLabel))

    override fun getLong(columnIndex: Int): Long = answerLong(columnIndex - 1)

    override fun getLong(columnLabel: String): Long = answerLong(index(columnLabel))

    override fun getFloat(columnIndex: Int): Float {
        TODO("Not yet implemented")
    }

    override fun getFloat(columnLabel: String): Float {
        TODO("Not yet implemented")
    }

    override fun getDouble(columnIndex: Int): Double = answerDouble(columnIndex - 1)

    override fun getDouble(columnLabel: String): Double = answerDouble(index(columnLabel))

    override fun getBigDecimal(columnIndex: Int, p1: Int): BigDecimal? = answerBigDecimal(columnIndex - 1)

    override fun getBigDecimal(columnLabel: String, p1: Int): BigDecimal? = answerBigDecimal(index(columnLabel))

    override fun getBigDecimal(columnIndex: Int): BigDecimal? = answerBigDecimal(columnIndex - 1)

    override fun getBigDecimal(columnLabel: String): BigDecimal? = answerBigDecimal(index(columnLabel))

    override fun getBytes(columnIndex: Int): ByteArray {
        TODO("Not yet implemented")
    }

    override fun getBytes(columnLabel: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun getDate(columnIndex: Int): Date? = answerDate(columnIndex - 1)

    override fun getDate(columnLabel: String): Date? = answerDate(index(columnLabel))

    override fun getDate(columnIndex: Int, p1: Calendar?): Date {
        TODO("Not yet implemented")
    }

    override fun getDate(columnLabel: String, p1: Calendar?): Date {
        TODO("Not yet implemented")
    }

    override fun getTime(columnIndex: Int): Time {
        TODO("Not yet implemented")
    }

    override fun getTime(columnLabel: String): Time {
        TODO("Not yet implemented")
    }

    override fun getTime(columnIndex: Int, p1: Calendar?): Time {
        TODO("Not yet implemented")
    }

    override fun getTime(columnLabel: String, p1: Calendar?): Time {
        TODO("Not yet implemented")
    }

    override fun getTimestamp(columnIndex: Int): Timestamp? = answerTimestamp(columnIndex - 1)

    override fun getTimestamp(columnLabel: String): Timestamp? = answerTimestamp(index(columnLabel))

    override fun getTimestamp(columnIndex: Int, p1: Calendar?): Timestamp {
        TODO("Not yet implemented")
    }

    override fun getTimestamp(columnLabel: String, p1: Calendar?): Timestamp {
        TODO("Not yet implemented")
    }

    override fun getAsciiStream(columnIndex: Int): InputStream {
        TODO("Not yet implemented")
    }

    override fun getAsciiStream(columnLabel: String): InputStream {
        TODO("Not yet implemented")
    }

    override fun getUnicodeStream(columnIndex: Int): InputStream {
        TODO("Not yet implemented")
    }

    override fun getUnicodeStream(columnLabel: String): InputStream {
        TODO("Not yet implemented")
    }

    override fun getBinaryStream(columnIndex: Int): InputStream {
        TODO("Not yet implemented")
    }

    override fun getBinaryStream(columnLabel: String): InputStream {
        TODO("Not yet implemented")
    }

    override fun getWarnings(): SQLWarning {
        TODO("Not yet implemented")
    }

    override fun clearWarnings() {
        TODO("Not yet implemented")
    }

    override fun getCursorName(): String {
        TODO("Not yet implemented")
    }

    override fun getMetaData(): ResultSetMetaData {
        return MockResultSetMetaData(this.columnIndices)
    }

    override fun getObject(columnIndex: Int): Any? = answerObject(columnIndex - 1)

    override fun getObject(columnLabel: String): Any? = answerObject(index(columnLabel))

    override fun getObject(columnIndex: Int, p1: MutableMap<String, Class<*>>?): Any {
        TODO("Not yet implemented")
    }

    override fun getObject(columnLabel: String, p1: MutableMap<String, Class<*>>?): Any {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> getObject(columnIndex: Int, p1: Class<T>): T? {
        if (p1 == OffsetDateTime::class.java) {
            return answerOffsetDateTime(columnIndex - 1) as T?
        } else {
            TODO("Not yet implemented")
        }
    }

    override fun <T : Any?> getObject(columnLabel: String, p1: Class<T>?): T? {
        if (p1 == OffsetDateTime::class.java) {
            return answerOffsetDateTime(index(columnLabel)) as T?
        } else {
            TODO("Not yet implemented")
        }
    }

    override fun findColumn(columnLabel: String): Int {
        val columnIndex = index(columnLabel)
        if (columnIndex == Int.MAX_VALUE) throw SQLException("Invalid column name")
        return columnIndex + 1
    }

    override fun getCharacterStream(columnIndex: Int): Reader {
        TODO("Not yet implemented")
    }

    override fun getCharacterStream(columnLabel: String): Reader {
        TODO("Not yet implemented")
    }

    override fun isBeforeFirst(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isAfterLast(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isFirst(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isLast(): Boolean {
        TODO("Not yet implemented")
    }

    override fun beforeFirst() {
        TODO("Not yet implemented")
    }

    override fun afterLast() {
        TODO("Not yet implemented")
    }

    override fun first(): Boolean {
        TODO("Not yet implemented")
    }

    override fun last(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getRow(): Int {
        TODO("Not yet implemented")
    }

    override fun absolute(columnIndex: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun relative(columnIndex: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun previous(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setFetchDirection(columnIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun getFetchDirection(): Int {
        TODO("Not yet implemented")
    }

    override fun setFetchSize(columnIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun getFetchSize(): Int {
        TODO("Not yet implemented")
    }

    override fun getType(): Int = ResultSet.TYPE_FORWARD_ONLY

    override fun getConcurrency(): Int {
        TODO("Not yet implemented")
    }

    override fun rowUpdated(): Boolean {
        TODO("Not yet implemented")
    }

    override fun rowInserted(): Boolean {
        TODO("Not yet implemented")
    }

    override fun rowDeleted(): Boolean {
        TODO("Not yet implemented")
    }

    override fun updateNull(columnIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun updateNull(columnLabel: String) {
        TODO("Not yet implemented")
    }

    override fun updateBoolean(columnIndex: Int, p1: Boolean) {
        TODO("Not yet implemented")
    }

    override fun updateBoolean(columnLabel: String, p1: Boolean) {
        TODO("Not yet implemented")
    }

    override fun updateByte(columnIndex: Int, p1: Byte) {
        TODO("Not yet implemented")
    }

    override fun updateByte(columnLabel: String, p1: Byte) {
        TODO("Not yet implemented")
    }

    override fun updateShort(columnIndex: Int, p1: Short) {
        TODO("Not yet implemented")
    }

    override fun updateShort(columnLabel: String, p1: Short) {
        TODO("Not yet implemented")
    }

    override fun updateInt(columnIndex: Int, p1: Int) {
        TODO("Not yet implemented")
    }

    override fun updateInt(columnLabel: String, p1: Int) {
        TODO("Not yet implemented")
    }

    override fun updateLong(columnIndex: Int, p1: Long) {
        TODO("Not yet implemented")
    }

    override fun updateLong(columnLabel: String, p1: Long) {
        TODO("Not yet implemented")
    }

    override fun updateFloat(columnIndex: Int, p1: Float) {
        TODO("Not yet implemented")
    }

    override fun updateFloat(columnLabel: String, p1: Float) {
        TODO("Not yet implemented")
    }

    override fun updateDouble(columnIndex: Int, p1: Double) {
        TODO("Not yet implemented")
    }

    override fun updateDouble(columnLabel: String, p1: Double) {
        TODO("Not yet implemented")
    }

    override fun updateBigDecimal(columnIndex: Int, p1: BigDecimal?) {
        TODO("Not yet implemented")
    }

    override fun updateBigDecimal(columnLabel: String, p1: BigDecimal?) {
        TODO("Not yet implemented")
    }

    override fun updateString(columnIndex: Int, p1: String?) {
        TODO("Not yet implemented")
    }

    override fun updateString(columnLabel: String, p1: String?) {
        TODO("Not yet implemented")
    }

    override fun updateBytes(columnIndex: Int, p1: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun updateBytes(columnLabel: String, p1: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun updateDate(columnIndex: Int, p1: Date?) {
        TODO("Not yet implemented")
    }

    override fun updateDate(columnLabel: String, p1: Date?) {
        TODO("Not yet implemented")
    }

    override fun updateTime(columnIndex: Int, p1: Time?) {
        TODO("Not yet implemented")
    }

    override fun updateTime(columnLabel: String, p1: Time?) {
        TODO("Not yet implemented")
    }

    override fun updateTimestamp(columnIndex: Int, p1: Timestamp?) {
        TODO("Not yet implemented")
    }

    override fun updateTimestamp(columnLabel: String, p1: Timestamp?) {
        TODO("Not yet implemented")
    }

    override fun updateAsciiStream(columnIndex: Int, p1: InputStream?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateAsciiStream(columnLabel: String, p1: InputStream?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateAsciiStream(columnIndex: Int, p1: InputStream?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateAsciiStream(columnLabel: String, p1: InputStream?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateAsciiStream(columnIndex: Int, p1: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun updateAsciiStream(columnLabel: String, p1: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun updateBinaryStream(columnIndex: Int, p1: InputStream?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateBinaryStream(columnLabel: String, p1: InputStream?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateBinaryStream(columnIndex: Int, p1: InputStream?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateBinaryStream(columnLabel: String, p1: InputStream?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateBinaryStream(columnIndex: Int, p1: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun updateBinaryStream(columnLabel: String, p1: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun updateCharacterStream(columnIndex: Int, p1: Reader?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateCharacterStream(columnLabel: String, p1: Reader?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateCharacterStream(columnIndex: Int, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateCharacterStream(columnLabel: String, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateCharacterStream(columnIndex: Int, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun updateCharacterStream(columnLabel: String, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun updateObject(columnIndex: Int, p1: Any?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateObject(columnIndex: Int, p1: Any?) {
        TODO("Not yet implemented")
    }

    override fun updateObject(columnLabel: String, p1: Any?, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun updateObject(columnLabel: String, p1: Any?) {
        TODO("Not yet implemented")
    }

    override fun insertRow() {
        TODO("Not yet implemented")
    }

    override fun updateRow() {
        TODO("Not yet implemented")
    }

    override fun deleteRow() {
        TODO("Not yet implemented")
    }

    override fun refreshRow() {
        TODO("Not yet implemented")
    }

    override fun cancelRowUpdates() {
        TODO("Not yet implemented")
    }

    override fun moveToInsertRow() {
        TODO("Not yet implemented")
    }

    override fun moveToCurrentRow() {
        TODO("Not yet implemented")
    }

    override fun getStatement(): Statement {
        TODO("Not yet implemented")
    }

    override fun getRef(columnIndex: Int): Ref {
        TODO("Not yet implemented")
    }

    override fun getRef(columnLabel: String): Ref {
        TODO("Not yet implemented")
    }

    override fun getBlob(columnIndex: Int): Blob {
        TODO("Not yet implemented")
    }

    override fun getBlob(columnLabel: String): Blob {
        TODO("Not yet implemented")
    }

    override fun getClob(columnIndex: Int): Clob {
        TODO("Not yet implemented")
    }

    override fun getClob(columnLabel: String): Clob {
        TODO("Not yet implemented")
    }

    override fun getArray(columnIndex: Int): SQLArray {
        TODO("Not yet implemented")
    }

    override fun getArray(columnLabel: String): SQLArray {
        TODO("Not yet implemented")
    }

    override fun getURL(columnIndex: Int): URL {
        TODO("Not yet implemented")
    }

    override fun getURL(columnLabel: String): URL {
        TODO("Not yet implemented")
    }

    override fun updateRef(columnIndex: Int, p1: Ref?) {
        TODO("Not yet implemented")
    }

    override fun updateRef(columnLabel: String, p1: Ref?) {
        TODO("Not yet implemented")
    }

    override fun updateBlob(columnIndex: Int, p1: Blob?) {
        TODO("Not yet implemented")
    }

    override fun updateBlob(columnLabel: String, p1: Blob?) {
        TODO("Not yet implemented")
    }

    override fun updateBlob(columnIndex: Int, p1: InputStream?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateBlob(columnLabel: String, p1: InputStream?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateBlob(columnIndex: Int, p1: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun updateBlob(columnLabel: String, p1: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun updateClob(columnIndex: Int, p1: Clob?) {
        TODO("Not yet implemented")
    }

    override fun updateClob(columnLabel: String, p1: Clob?) {
        TODO("Not yet implemented")
    }

    override fun updateClob(columnIndex: Int, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateClob(columnLabel: String, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateClob(columnIndex: Int, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun updateClob(columnLabel: String, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun updateArray(columnIndex: Int, p1: Array?) {
        TODO("Not yet implemented")
    }

    override fun updateArray(columnLabel: String, p1: Array?) {
        TODO("Not yet implemented")
    }

    override fun getRowId(columnIndex: Int): RowId {
        TODO("Not yet implemented")
    }

    override fun getRowId(columnLabel: String): RowId {
        TODO("Not yet implemented")
    }

    override fun updateRowId(columnIndex: Int, p1: RowId?) {
        TODO("Not yet implemented")
    }

    override fun updateRowId(columnLabel: String, p1: RowId?) {
        TODO("Not yet implemented")
    }

    override fun getHoldability(): Int {
        TODO("Not yet implemented")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun updateNString(columnIndex: Int, p1: String?) {
        TODO("Not yet implemented")
    }

    override fun updateNString(columnLabel: String, p1: String?) {
        TODO("Not yet implemented")
    }

    override fun updateNClob(columnIndex: Int, p1: NClob?) {
        TODO("Not yet implemented")
    }

    override fun updateNClob(columnLabel: String, p1: NClob?) {
        TODO("Not yet implemented")
    }

    override fun updateNClob(columnIndex: Int, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateNClob(columnLabel: String, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateNClob(columnIndex: Int, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun updateNClob(columnLabel: String, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun getNClob(columnIndex: Int): NClob {
        TODO("Not yet implemented")
    }

    override fun getNClob(columnLabel: String): NClob {
        TODO("Not yet implemented")
    }

    override fun getSQLXML(columnIndex: Int): SQLXML {
        TODO("Not yet implemented")
    }

    override fun getSQLXML(columnLabel: String): SQLXML {
        TODO("Not yet implemented")
    }

    override fun updateSQLXML(columnIndex: Int, p1: SQLXML?) {
        TODO("Not yet implemented")
    }

    override fun updateSQLXML(columnLabel: String, p1: SQLXML?) {
        TODO("Not yet implemented")
    }

    override fun getNString(columnIndex: Int): String {
        TODO("Not yet implemented")
    }

    override fun getNString(columnLabel: String): String {
        TODO("Not yet implemented")
    }

    override fun getNCharacterStream(columnIndex: Int): Reader {
        TODO("Not yet implemented")
    }

    override fun getNCharacterStream(columnLabel: String): Reader {
        TODO("Not yet implemented")
    }

    override fun updateNCharacterStream(columnIndex: Int, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateNCharacterStream(columnLabel: String, p1: Reader?, p2: Long) {
        TODO("Not yet implemented")
    }

    override fun updateNCharacterStream(columnIndex: Int, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun updateNCharacterStream(columnLabel: String, p1: Reader?) {
        TODO("Not yet implemented")
    }

    override fun toString(): String = tag

    companion object {
        private val logger = LoggerFactory.getLogger(MockResultSet::class.java)
        private val counter = AtomicLong(0)
        internal const val THE_ANSWER_TO_THE_ULTIMATE_QUESTION = 42

        /**
         * Creates the mock ResultSet.
         *
         * @param columnNames the names of the columns
         * @param data the data to be returned from the mocked ResultSet
         * @param usages the number of times this resultset is used, defaults to 1
         * @return a mocked ResultSet
         * @throws SQLException if building the mocked ResultSet fails
         */
        @JvmStatic
        @JvmOverloads
        @Throws(SQLException::class)
        fun create(tag: String, columnNames: Array<String>?, data: Array<Array<Any?>>, usages: Int = 1): ResultSet {
            return MockResultSet(tag, columnNames, data, usages)
        }

        /**
         * Adds a mock ResultSet object to the queue.
         *
         * @param columnNames the names of the columns
         * @param data the data to be returned from the mocked ResultSet
         * @param usages the number of times this resultset is used, defaults to 1
         */
        @JvmStatic
        @JvmOverloads
        fun add(tag: String, columnNames: Array<String>?, data: Array<Array<Any?>>, usages: Int = 1) {
            MockSQLBuilderProvider.addResultSet(create(tag, columnNames, data, usages))
        }

        /**
         * Creates the mock ResultSet.
         *
         * @param data the data to be returned from the mocked ResultSet
         * @return a mocked ResultSet
         * @throws SQLException if building the mocked ResultSet fails
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun create(tag: String, data: Array<Array<Any?>>): ResultSet {
            return MockResultSet(tag, null, data)
        }

        /**
         * Adds a mock ResultSet object to the queue.
         *
         * @param data the data to be returned from the mocked ResultSet
         */
        @JvmStatic
        fun add(tag: String, data: Array<Array<Any?>>) {
            val rs = create(tag, data)
            MockSQLBuilderProvider.addResultSet(rs)
        }

        /**
         * Creates the mock ResultSet.
         *
         * @param csv the data to be returned from the mocked ResultSet
         * @return a mocked ResultSet
         * @throws SQLException if building the mocked ResultSet fails
         */
        @JvmStatic
        @JvmOverloads
        @Throws(SQLException::class)
        fun create(tag: String, csv: String, withLabels: Boolean, generated: Boolean = false): ResultSet {
            try {
                CSVReader(StringReader(csv)).use { csvReader ->
                    val data: MutableList<Array<String?>> = csvReader.readAll()
                    val columnNames = if (withLabels) data.removeAt(0).map { it!! }.toTypedArray() else null
                    return MockResultSet(tag, columnNames, data.toTypedArray() as Array<Array<Any?>>).apply {
                        this.generated = generated
                    }
                }
            } catch (ex: IOException) {
                logger.error("Cannot parse CSV {}", csv)
                throw SQLException("Invalid data")
            } catch (ex: CsvException) {
                logger.error("Cannot parse CSV {}", csv)
                throw SQLException("Invalid data")
            }
        }

        /**
         * Adds a mock ResultSet object to the queue.
         *
         * @param csv the data to be returned from the mocked ResultSet
         */
        @JvmStatic
        fun add(tag: String, csv: String, withLabels: Boolean) {
            MockSQLBuilderProvider.addResultSet(create(tag, csv, withLabels, false))
        }

        /**
         * Creates the mock ResultSet.
         *
         * @param csv A CSV file containing the data, optionally with a header line
         * @return a mocked ResultSet
         * @throws SQLException in case of errors
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun create(tag: String, csv: InputStream, withLabels: Boolean): ResultSet {
            try {
                CSVReader(InputStreamReader(csv)).use { csvReader ->
                    val data = csvReader.readAll()
                    val columnNames = if (withLabels) data.removeAt(0) else null
                    return MockResultSet(tag, columnNames, data.toTypedArray() as Array<Array<Any?>>)
                }
            } catch (ex: Exception) {
                logger.error("Cannot parse CSV {}", csv)
                throw SQLException("Invalid data")
            }
        }

        /**
         * Adds a mock ResultSet object to the queue.
         *
         * @param csv A CSV file containing the data, optionally with a header line
         */
        @JvmStatic
        @JvmOverloads
        fun add(tag: String, csv: InputStream, withLabels: Boolean = true) {
            MockSQLBuilderProvider.addResultSet(create(tag, csv, withLabels))
        }

        /**
         * Creates the mock ResultSet.
         *
         * @param csvs the data to be returned from the mocked ResultSet
         * @return a mocked ResultSet
         * @throws SQLException if building the mocked ResultSet fails
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun create(tag: String, labels: String, vararg csvs: String): ResultSet {
            try {
                CSVReader(StringReader(labels)).use { csvReader1 ->
                    val columnNames = csvReader1.readNext()
                    val data = mutableListOf<Array<String>>()
                    for (csv in csvs) {
                        CSVReader(StringReader(csv)).use { csvReader2 -> data.addAll(csvReader2.readAll()) }
                    }
                    return MockResultSet(tag, columnNames, data.toTypedArray() as Array<Array<Any?>>)
                }
            } catch (ex: IOException) {
                logger.error("Cannot parse CSV {}", listOf(*csvs))
                throw SQLException("Invalid data")
            } catch (ex: CsvException) {
                logger.error("Cannot parse CSV {}", listOf(*csvs))
                throw SQLException("Invalid data")
            }
        }

        /**
         * Adds a mock ResultSet object to the queue.
         *
         * @param csvs the data to be returned from the mocked ResultSet
         */
        @JvmStatic
        fun add(tag: String, labels: String, vararg csvs: String) {
            MockSQLBuilderProvider.addResultSet(create(tag, labels, *csvs))
        }

        /**
         * Creates an empty mock ResultSet.
         *
         * @return a mocked ResultSet
         * @throws SQLException if building the mocked ResultSet fails
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun empty(tag: String): ResultSet {
            return MockResultSet(tag, arrayOf(), arrayOf(), 0)
        }

        /**
         * Creates an empty mock ResultSet.
         *
         * @return a mocked ResultSet
         */
        @JvmStatic
        fun addEmpty(tag: String) {
            MockSQLBuilderProvider.addResultSet(empty(tag))
        }

        @JvmStatic
        @Throws(SQLException::class)
        fun broken(tag: String): ResultSet {
            return MockResultSet(tag, arrayOf(), arrayOf(), -1)
        }

        @JvmStatic
        fun addBroken(tag: String) {
            MockSQLBuilderProvider.addResultSet(broken(tag))
        }
    }

    init {
        this.tag = tag.ifEmpty { "MockResultSet#${counter.incrementAndGet()}" }
        val columnNames: Array<String> = if (names.isNullOrEmpty()) {
            Array(data.getOrNull(0)?.size ?: 0) { i: Int -> "COLUMN${i + 1}" }
        } else {
            names
        }
        columnIndices = columnNames.mapIndexed { i, n -> n.uppercase() to i }.toMap()
    }
}
