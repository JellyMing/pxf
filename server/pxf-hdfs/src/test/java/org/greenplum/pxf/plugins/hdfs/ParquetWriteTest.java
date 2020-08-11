package org.greenplum.pxf.plugins.hdfs;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.HadoopReadOptions;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.metadata.FileMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.greenplum.pxf.api.OneField;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.io.DataType;
import org.greenplum.pxf.api.model.Accessor;
import org.greenplum.pxf.api.model.ConfigurationFactory;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.api.model.Resolver;
import org.greenplum.pxf.api.utilities.ColumnDescriptor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.math.BigDecimal.ROUND_UNNECESSARY;
import static org.apache.parquet.hadoop.ParquetOutputFormat.BLOCK_SIZE;
import static org.apache.parquet.hadoop.ParquetOutputFormat.DICTIONARY_PAGE_SIZE;
import static org.apache.parquet.hadoop.ParquetOutputFormat.ENABLE_DICTIONARY;
import static org.apache.parquet.hadoop.ParquetOutputFormat.PAGE_SIZE;
import static org.apache.parquet.hadoop.ParquetOutputFormat.WRITER_VERSION;
import static org.greenplum.pxf.plugins.hdfs.parquet.ParquetTypeConverter.bytesToTimestamp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParquetWriteTest {

    private Accessor accessor;
    private Resolver resolver;
    private RequestContext context;
    private Configuration configuration;

    protected List<ColumnDescriptor> columnDescriptors;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Before
    public void setup() {

        columnDescriptors = new ArrayList<>();

        ConfigurationFactory mockConfigurationFactory = mock(ConfigurationFactory.class);

        accessor = new ParquetFileAccessor(mockConfigurationFactory);
        resolver = new ParquetResolver(mockConfigurationFactory);
        context = new RequestContext();

        context.setConfig("fakeConfig");
        context.setServerName("fakeServerName");
        context.setUser("fakeUser");
        context.setProfileScheme("localfile");
        context.setSegmentId(4);
        context.setRequestType(RequestContext.RequestType.WRITE_BRIDGE);
        context.setTupleDescription(columnDescriptors);

        configuration = new Configuration();

        when(mockConfigurationFactory.
                initConfiguration(context.getConfig(), context.getServerName(), context.getUser(), context.getAdditionalConfigProps()))
                .thenReturn(configuration);
    }

    @Test
    public void testDefaultWriteOptions() throws Exception {

        columnDescriptors.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        context.setDataSource(temp.getRoot() + "/out/");
        context.setTransactionId("XID-XYZ-123453");

        accessor.initialize(context);
        assertTrue(accessor.openForWrite());
        accessor.closeForWrite();

        assertEquals(1024 * 1024, configuration.getInt(PAGE_SIZE, -1));
        assertEquals(1024 * 1024, configuration.getInt(DICTIONARY_PAGE_SIZE, -1));
        assertTrue(configuration.getBoolean(ENABLE_DICTIONARY, false));
        assertEquals("PARQUET_1_0", configuration.get(WRITER_VERSION));
        assertEquals(8 * 1024 * 1024, configuration.getLong(BLOCK_SIZE, -1));
    }

    @Test
    public void testSettingPAGE_SIZEOption() throws Exception {

        columnDescriptors.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        context.setDataSource(temp.getRoot() + "/out/");
        context.setTransactionId("XID-XYZ-123453");
        context.addOption("PAGE_SIZE", "5242880");

        accessor.initialize(context);
        assertTrue(accessor.openForWrite());
        accessor.closeForWrite();

        assertEquals(5 * 1024 * 1024, configuration.getInt(PAGE_SIZE, -1));
    }

    @Test
    public void testSettingDICTIONARY_PAGE_SIZEOption() throws Exception {

        columnDescriptors.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        context.setDataSource(temp.getRoot() + "/out/");
        context.setTransactionId("XID-XYZ-123453");
        context.addOption("DICTIONARY_PAGE_SIZE", "5242880");

        accessor.initialize(context);
        assertTrue(accessor.openForWrite());
        accessor.closeForWrite();

        assertEquals(5 * 1024 * 1024, configuration.getInt(DICTIONARY_PAGE_SIZE, -1));
    }

    @Test
    public void testSettingENABLE_DICTIONARYOption() throws Exception {

        columnDescriptors.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        context.setDataSource(temp.getRoot() + "/out/");
        context.setTransactionId("XID-XYZ-123453");
        context.addOption("ENABLE_DICTIONARY", "false");

        accessor.initialize(context);
        assertTrue(accessor.openForWrite());
        accessor.closeForWrite();

        assertFalse(configuration.getBoolean(ENABLE_DICTIONARY, true));
    }

    @Test
    public void testSettingPARQUET_VERSIONOption() throws Exception {

        columnDescriptors.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        context.setDataSource(temp.getRoot() + "/out/");
        context.setTransactionId("XID-XYZ-123453");
        context.addOption("PARQUET_VERSION", "v2");

        accessor.initialize(context);
        assertTrue(accessor.openForWrite());
        accessor.closeForWrite();

        assertEquals("PARQUET_2_0", configuration.get(WRITER_VERSION));
    }

    @Test
    public void testSettingROWGROUP_SIZEOption() throws Exception {

        columnDescriptors.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        context.setDataSource(temp.getRoot() + "/out/");
        context.setTransactionId("XID-XYZ-123453");
        context.addOption("ROWGROUP_SIZE", "33554432");

        accessor.initialize(context);
        assertTrue(accessor.openForWrite());
        accessor.closeForWrite();

        assertEquals(32 * 1024 * 1024, configuration.getInt(BLOCK_SIZE, -1));
    }

    @Test
    public void testWriteInt() throws Exception {

        String path = temp.getRoot() + "/out/int/";

        columnDescriptors.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123456");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with INT values from 0 to 9
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.INTEGER.getOID(), i));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // There is no logical annotation, the physical type is INT32
        assertNull(schema.getType(0).getLogicalTypeAnnotation());
        assertEquals(0, fileReader.read().getInteger(0, 0));
        assertEquals(1, fileReader.read().getInteger(0, 0));
        assertEquals(2, fileReader.read().getInteger(0, 0));
        assertEquals(3, fileReader.read().getInteger(0, 0));
        assertEquals(4, fileReader.read().getInteger(0, 0));
        assertEquals(5, fileReader.read().getInteger(0, 0));
        assertEquals(6, fileReader.read().getInteger(0, 0));
        assertEquals(7, fileReader.read().getInteger(0, 0));
        assertEquals(8, fileReader.read().getInteger(0, 0));
        assertEquals(9, fileReader.read().getInteger(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteText() throws Exception {
        String path = temp.getRoot() + "/out/text/";
        columnDescriptors.add(new ColumnDescriptor("name", DataType.TEXT.getOID(), 0, "text", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123457");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with TEXT values of a repeated i + 1 times
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.TEXT.getOID(), StringUtils.repeat("a", i + 1)));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is binary, logical type is String
        assertTrue(schema.getType(0).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation);
        assertEquals("a", fileReader.read().getString(0, 0));
        assertEquals("aa", fileReader.read().getString(0, 0));
        assertEquals("aaa", fileReader.read().getString(0, 0));
        assertEquals("aaaa", fileReader.read().getString(0, 0));
        assertEquals("aaaaa", fileReader.read().getString(0, 0));
        assertEquals("aaaaaa", fileReader.read().getString(0, 0));
        assertEquals("aaaaaaa", fileReader.read().getString(0, 0));
        assertEquals("aaaaaaaa", fileReader.read().getString(0, 0));
        assertEquals("aaaaaaaaa", fileReader.read().getString(0, 0));
        assertEquals("aaaaaaaaaa", fileReader.read().getString(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteDate() throws Exception {
        String path = temp.getRoot() + "/out/date/";
        columnDescriptors.add(new ColumnDescriptor("cdate", DataType.DATE.getOID(), 0, "date", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123458");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with DATE from 2020-08-01 to 2020-08-10
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.DATE.getOID(), String.format("2020-08-%02d", i + 1)));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is binary, logical type is Date
        assertTrue(schema.getType(0).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation);
        assertEquals("2020-08-01", fileReader.read().getString(0, 0));
        assertEquals("2020-08-02", fileReader.read().getString(0, 0));
        assertEquals("2020-08-03", fileReader.read().getString(0, 0));
        assertEquals("2020-08-04", fileReader.read().getString(0, 0));
        assertEquals("2020-08-05", fileReader.read().getString(0, 0));
        assertEquals("2020-08-06", fileReader.read().getString(0, 0));
        assertEquals("2020-08-07", fileReader.read().getString(0, 0));
        assertEquals("2020-08-08", fileReader.read().getString(0, 0));
        assertEquals("2020-08-09", fileReader.read().getString(0, 0));
        assertEquals("2020-08-10", fileReader.read().getString(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteFloat8() throws Exception {
        String path = temp.getRoot() + "/out/float/";
        columnDescriptors.add(new ColumnDescriptor("amt", DataType.FLOAT8.getOID(), 0, "float8", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123459");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with DATE from 2020-08-01 to 2020-08-10
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.FLOAT8.getOID(), 1.1 * i));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is binary, logical type is Date
        assertNull(schema.getType(0).getLogicalTypeAnnotation());
        assertEquals(0, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(1.1, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(2.2, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(3.3, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(4.4, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(5.5, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(6.6, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(7.7, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(8.8, fileReader.read().getDouble(0, 0), 0.01);
        assertEquals(9.9, fileReader.read().getDouble(0, 0), 0.01);
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteBoolean() throws Exception {
        String path = temp.getRoot() + "/out/boolean/";
        columnDescriptors.add(new ColumnDescriptor("b", DataType.BOOLEAN.getOID(), 5, "bool", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123460");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with boolean values
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.BOOLEAN.getOID(), i % 2 == 0));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is boolean
        assertNull(schema.getType(0).getLogicalTypeAnnotation());
        assertTrue(fileReader.read().getBoolean(0, 0));
        assertFalse(fileReader.read().getBoolean(0, 0));
        assertTrue(fileReader.read().getBoolean(0, 0));
        assertFalse(fileReader.read().getBoolean(0, 0));
        assertTrue(fileReader.read().getBoolean(0, 0));
        assertFalse(fileReader.read().getBoolean(0, 0));
        assertTrue(fileReader.read().getBoolean(0, 0));
        assertFalse(fileReader.read().getBoolean(0, 0));
        assertTrue(fileReader.read().getBoolean(0, 0));
        assertFalse(fileReader.read().getBoolean(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteTimestamp() throws Exception {
        String path = temp.getRoot() + "/out/timestamp/";
        columnDescriptors.add(new ColumnDescriptor("tm", DataType.TIMESTAMP.getOID(), 0, "timestamp", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123462");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with timestamp values
        for (int i = 0; i < 10; i++) {

            Instant timestamp = Instant.parse(String.format("2020-08-%02dT04:00:05Z", i + 1)); // UTC
            ZonedDateTime localTime = timestamp.atZone(ZoneId.systemDefault());
            String localTimestampString = localTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // should be "2020-08-%02dT04:00:05Z" in PST

            List<OneField> record = Collections.singletonList(new OneField(DataType.TIMESTAMP.getOID(), localTimestampString));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is INT96
        assertNull(schema.getType(0).getLogicalTypeAnnotation());

        for (int i = 0; i < 10; i++) {

            Instant timestamp = Instant.parse(String.format("2020-08-%02dT04:00:05Z", i + 1)); // UTC
            ZonedDateTime localTime = timestamp.atZone(ZoneId.systemDefault());
            String localTimestampString = localTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // should be "2020-08-%02dT04:00:05Z" in PST

            assertEquals(localTimestampString, bytesToTimestamp(fileReader.read().getInt96(0, 0).getBytes()));
        }
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteBigInt() throws Exception {
        String path = temp.getRoot() + "/out/bigint/";
        columnDescriptors.add(new ColumnDescriptor("bg", DataType.BIGINT.getOID(), 0, "bigint", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123463");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with bigint values
        for (int i = 0; i < 10; i++) {
            long value = (long) Integer.MAX_VALUE + i;
            List<OneField> record = Collections.singletonList(new OneField(DataType.BIGINT.getOID(), value));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is INT96
        assertNull(schema.getType(0).getLogicalTypeAnnotation());
        assertEquals(2147483647L, fileReader.read().getLong(0, 0));
        assertEquals(2147483648L, fileReader.read().getLong(0, 0));
        assertEquals(2147483649L, fileReader.read().getLong(0, 0));
        assertEquals(2147483650L, fileReader.read().getLong(0, 0));
        assertEquals(2147483651L, fileReader.read().getLong(0, 0));
        assertEquals(2147483652L, fileReader.read().getLong(0, 0));
        assertEquals(2147483653L, fileReader.read().getLong(0, 0));
        assertEquals(2147483654L, fileReader.read().getLong(0, 0));
        assertEquals(2147483655L, fileReader.read().getLong(0, 0));
        assertEquals(2147483656L, fileReader.read().getLong(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteBytea() throws Exception {
        String path = temp.getRoot() + "/out/bytea/";
        columnDescriptors.add(new ColumnDescriptor("bin", DataType.BYTEA.getOID(), 0, "bytea", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123464");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with bytea values
        for (int i = 0; i < 10; i++) {
            byte[] value = Binary.fromString(StringUtils.repeat("a", i + 1)).getBytes();
            List<OneField> record = Collections.singletonList(new OneField(DataType.BYTEA.getOID(), value));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is BINARY
        assertNull(schema.getType(0).getLogicalTypeAnnotation());
        assertEquals(Binary.fromString("a"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaaa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaaaa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaaaaa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaaaaaa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaaaaaaa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaaaaaaaa"), fileReader.read().getBinary(0, 0));
        assertEquals(Binary.fromString("aaaaaaaaaa"), fileReader.read().getBinary(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteSmallInt() throws Exception {
        String path = temp.getRoot() + "/out/smallint/";
        columnDescriptors.add(new ColumnDescriptor("sml", DataType.SMALLINT.getOID(), 0, "int2", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123465");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with bigint values
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.SMALLINT.getOID(), (short) i));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is INT
        assertTrue(schema.getType(0).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation);
        assertEquals(0, fileReader.read().getInteger(0, 0));
        assertEquals(1, fileReader.read().getInteger(0, 0));
        assertEquals(2, fileReader.read().getInteger(0, 0));
        assertEquals(3, fileReader.read().getInteger(0, 0));
        assertEquals(4, fileReader.read().getInteger(0, 0));
        assertEquals(5, fileReader.read().getInteger(0, 0));
        assertEquals(6, fileReader.read().getInteger(0, 0));
        assertEquals(7, fileReader.read().getInteger(0, 0));
        assertEquals(8, fileReader.read().getInteger(0, 0));
        assertEquals(9, fileReader.read().getInteger(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteReal() throws Exception {
        String path = temp.getRoot() + "/out/real/";
        columnDescriptors.add(new ColumnDescriptor("r", DataType.REAL.getOID(), 0, "real", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123466");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with real values
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.REAL.getOID(), 1.1F * i));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is FLOAT
        assertNull(schema.getType(0).getLogicalTypeAnnotation());
        assertEquals(0F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(1.1F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(2.2F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(3.3F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(4.4F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(5.5F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(6.6F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(7.7F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(8.8F, fileReader.read().getFloat(0, 0), 0.001);
        assertEquals(9.9F, fileReader.read().getFloat(0, 0), 0.001);
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteVarchar() throws Exception {
        String path = temp.getRoot() + "/out/varchar/";
        columnDescriptors.add(new ColumnDescriptor("vc1", DataType.VARCHAR.getOID(), 0, "varchar", new Integer[]{5}));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123467");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with varchar values
        for (int i = 0; i < 10; i++) {
            String s = StringUtils.repeat("b", i % 5);
            List<OneField> record = Collections.singletonList(new OneField(DataType.VARCHAR.getOID(), s));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is BINARY
        assertTrue(schema.getType(0).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation);
        assertEquals("", fileReader.read().getString(0, 0));
        assertEquals("b", fileReader.read().getString(0, 0));
        assertEquals("bb", fileReader.read().getString(0, 0));
        assertEquals("bbb", fileReader.read().getString(0, 0));
        assertEquals("bbbb", fileReader.read().getString(0, 0));
        assertEquals("", fileReader.read().getString(0, 0));
        assertEquals("b", fileReader.read().getString(0, 0));
        assertEquals("bb", fileReader.read().getString(0, 0));
        assertEquals("bbb", fileReader.read().getString(0, 0));
        assertEquals("bbbb", fileReader.read().getString(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteChar() throws Exception {
        String path = temp.getRoot() + "/out/char/";
        columnDescriptors.add(new ColumnDescriptor("c1", DataType.BPCHAR.getOID(), 0, "char", new Integer[]{3}));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123468");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with char values
        for (int i = 0; i < 10; i++) {
            String s = StringUtils.repeat("c", i % 3);
            List<OneField> record = Collections.singletonList(new OneField(DataType.BPCHAR.getOID(), s));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is BINARY
        assertTrue(schema.getType(0).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation);
        assertEquals("", fileReader.read().getString(0, 0));
        assertEquals("c", fileReader.read().getString(0, 0));
        assertEquals("cc", fileReader.read().getString(0, 0));
        assertEquals("", fileReader.read().getString(0, 0));
        assertEquals("c", fileReader.read().getString(0, 0));
        assertEquals("cc", fileReader.read().getString(0, 0));
        assertEquals("", fileReader.read().getString(0, 0));
        assertEquals("c", fileReader.read().getString(0, 0));
        assertEquals("cc", fileReader.read().getString(0, 0));
        assertEquals("", fileReader.read().getString(0, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteNumeric() throws Exception {
        String path = temp.getRoot() + "/out/numeric/";
        columnDescriptors.add(new ColumnDescriptor("dec1", DataType.NUMERIC.getOID(), 0, "numeric", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123469");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with bigint values
        for (int i = 0; i < 10; i++) {
            List<OneField> record = Collections.singletonList(new OneField(DataType.NUMERIC.getOID(), String.format("%d.%d", (i + 1), (i + 2))));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        // Physical type is BINARY
        assertTrue(schema.getType(0).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation);
        assertEquals(new BigDecimal("1.2").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("2.3").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("3.4").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("4.5").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("5.6").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("6.7").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("7.8").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("8.9").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("9.10").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("10.11").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(fileReader.read().getBinary(0, 0).getBytes()), 18));
        assertNull(fileReader.read());
        fileReader.close();
    }

    @Test
    public void testWriteMultipleTypes() throws Exception {
        String path = temp.getRoot() + "/out/multiple/";
        columnDescriptors.add(new ColumnDescriptor("dec1", DataType.NUMERIC.getOID(), 0, "numeric", null));
        columnDescriptors.add(new ColumnDescriptor("c1", DataType.BPCHAR.getOID(), 1, "char", new Integer[]{3}));
        columnDescriptors.add(new ColumnDescriptor("tm", DataType.TIMESTAMP.getOID(), 2, "timestamp", null));
        columnDescriptors.add(new ColumnDescriptor("bin", DataType.BYTEA.getOID(), 3, "bytea", null));
        columnDescriptors.add(new ColumnDescriptor("name", DataType.TEXT.getOID(), 4, "text", null));

        context.setDataSource(path);
        context.setTransactionId("XID-XYZ-123470");

        accessor.initialize(context);
        resolver.initialize(context);

        assertTrue(accessor.openForWrite());

        // write parquet file with bigint values
        for (int i = 0; i < 3; i++) {
            List<OneField> record = new ArrayList<>();
            record.add(new OneField(DataType.NUMERIC.getOID(), String.format("%d.%d", (i + 1), (i + 2))));

            String s = StringUtils.repeat("d", i % 3);
            record.add(new OneField(DataType.BPCHAR.getOID(), s));

            Instant timestamp = Instant.parse(String.format("2020-08-%02dT04:00:05Z", i + 1)); // UTC
            ZonedDateTime localTime = timestamp.atZone(ZoneId.systemDefault());
            String localTimestampString = localTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // should be "2020-08-%02dT04:00:05Z" in PST
            record.add(new OneField(DataType.TIMESTAMP.getOID(), localTimestampString));

            byte[] value = Binary.fromString(StringUtils.repeat("e", i + 1)).getBytes();
            record.add(new OneField(DataType.BYTEA.getOID(), value));

            record.add(new OneField(DataType.TEXT.getOID(), StringUtils.repeat("f", i + 1)));
            OneRow rowToWrite = resolver.setFields(record);
            assertTrue(accessor.writeNextObject(rowToWrite));
        }

        accessor.closeForWrite();

        // Validate write
        Path expectedFile = new Path(HcfsType.LOCALFILE.getUriForWrite(configuration, context, true) + ".snappy.parquet");
        assertTrue(expectedFile.getFileSystem(configuration).exists(expectedFile));

        MessageType schema = validateFooter(expectedFile, 5, 3);

        ParquetReader<Group> fileReader = ParquetReader.builder(new GroupReadSupport(), expectedFile)
                .withConf(configuration)
                .build();

        assertTrue(schema.getType(0).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation); //numeric
        assertTrue(schema.getType(1).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation); //bpchar
        assertNull(schema.getType(2).getLogicalTypeAnnotation()); //timestamp
        assertNull(schema.getType(3).getLogicalTypeAnnotation()); //bytea
        assertTrue(schema.getType(4).getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation); //text
        Group row0 = fileReader.read();
        Group row1 = fileReader.read();
        Group row2 = fileReader.read();

        assertEquals(new BigDecimal("1.2").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(row0.getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("2.3").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(row1.getBinary(0, 0).getBytes()), 18));
        assertEquals(new BigDecimal("3.4").setScale(18, ROUND_UNNECESSARY), new BigDecimal(new BigInteger(row2.getBinary(0, 0).getBytes()), 18));

        assertEquals("", row0.getString(1, 0));
        assertEquals("d", row1.getString(1, 0));
        assertEquals("dd", row2.getString(1, 0));

        Instant timestamp0 = Instant.parse("2020-08-01T04:00:05Z"); // UTC
        ZonedDateTime localTime0 = timestamp0.atZone(ZoneId.systemDefault());
        String localTimestampString0 = localTime0.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // should be "2020-08-%02dT04:00:05Z" in PST
        assertEquals(localTimestampString0, bytesToTimestamp(row0.getInt96(2, 0).getBytes()));
        Instant timestamp1 = Instant.parse("2020-08-02T04:00:05Z"); // UTC
        ZonedDateTime localTime1 = timestamp1.atZone(ZoneId.systemDefault());
        String localTimestampString1 = localTime1.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // should be "2020-08-%02dT04:00:05Z" in PST
        assertEquals(localTimestampString1, bytesToTimestamp(row1.getInt96(2, 0).getBytes()));
        Instant timestamp2 = Instant.parse("2020-08-03T04:00:05Z"); // UTC
        ZonedDateTime localTime2 = timestamp2.atZone(ZoneId.systemDefault());
        String localTimestampString2 = localTime2.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // should be "2020-08-%02dT04:00:05Z" in PST
        assertEquals(localTimestampString2, bytesToTimestamp(row2.getInt96(2, 0).getBytes()));

        assertEquals(Binary.fromString("e"), row0.getBinary(3, 0));
        assertEquals(Binary.fromString("ee"), row1.getBinary(3, 0));
        assertEquals(Binary.fromString("eee"), row2.getBinary(3, 0));

        assertEquals("f", row0.getString(4, 0));
        assertEquals("ff", row1.getString(4, 0));
        assertEquals("fff", row2.getString(4, 0));
        assertNull(fileReader.read());
        fileReader.close();
    }

    private MessageType validateFooter(Path parquetFile) throws IOException {
        return validateFooter(parquetFile, 1, 10);
    }

    private MessageType validateFooter(Path parquetFile, int numCols, int numRows) throws IOException {

        ParquetReadOptions parquetReadOptions = HadoopReadOptions
                .builder(configuration)
                .build();
        HadoopInputFile inputFile = HadoopInputFile.fromPath(parquetFile, configuration);

        try (ParquetFileReader parquetFileReader =
                     ParquetFileReader.open(inputFile, parquetReadOptions)) {
            FileMetaData metadata = parquetFileReader.getFileMetaData();

            ParquetMetadata readFooter = parquetFileReader.getFooter();
            assertEquals(1, readFooter.getBlocks().size()); // one block

            BlockMetaData block0 = readFooter.getBlocks().get(0);
            assertEquals(numCols, block0.getColumns().size()); // one column
            assertEquals(numRows, block0.getRowCount()); // 10 rows in this block

            ColumnChunkMetaData column0 = block0.getColumns().get(0);
            assertEquals(CompressionCodecName.SNAPPY, column0.getCodec());
            return metadata.getSchema();
        }
    }
}