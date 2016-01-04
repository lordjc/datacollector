/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.lib;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ListBeanModel;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.ValueChooserModel;
import com.streamsets.pipeline.api.impl.XMLChar;
import com.streamsets.pipeline.common.DataFormatConstants;
import com.streamsets.pipeline.config.CharsetChooserValues;
import com.streamsets.pipeline.config.Compression;
import com.streamsets.pipeline.config.CompressionChooserValues;
import com.streamsets.pipeline.config.CsvHeader;
import com.streamsets.pipeline.config.CsvHeaderChooserValues;
import com.streamsets.pipeline.config.CsvMode;
import com.streamsets.pipeline.config.CsvModeChooserValues;
import com.streamsets.pipeline.config.CsvRecordType;
import com.streamsets.pipeline.config.CsvRecordTypeChooserValues;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.JsonMode;
import com.streamsets.pipeline.config.JsonModeChooserValues;
import com.streamsets.pipeline.config.LogMode;
import com.streamsets.pipeline.config.LogModeChooserValues;
import com.streamsets.pipeline.config.OnParseError;
import com.streamsets.pipeline.config.OnParseErrorChooserValues;
import com.streamsets.pipeline.lib.parser.DataParserFactory;
import com.streamsets.pipeline.lib.parser.DataParserFactoryBuilder;
import com.streamsets.pipeline.lib.parser.avro.AvroDataParserFactory;
import com.streamsets.pipeline.lib.parser.log.LogDataFormatValidator;
import com.streamsets.pipeline.lib.parser.log.RegExConfig;
import com.streamsets.pipeline.lib.parser.xml.XmlDataParserFactory;
import com.streamsets.pipeline.lib.util.DelimitedDataConstants;
import com.streamsets.pipeline.lib.util.ProtobufConstants;
import com.streamsets.pipeline.stage.common.DataFormatErrors;
import com.streamsets.pipeline.stage.common.DataFormatGroups;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Instances of this object must be called 'dataFormatConfig' exactly for error
 * messages to be placed in the correct location on the UI.
 */
public class DataParserFormatConfig {
  private static final String DEFAULT_REGEX =
      "^(\\S+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\S+) (\\S+) (\\S+)\" (\\d{3}) (\\d+)";
  private static final String DEFAULT_APACHE_CUSTOM_LOG_FORMAT = "%h %l %u %t \"%r\" %>s %b";
  private static final String DEFAULT_GROK_PATTERN = "%{COMMONAPACHELOG}";
  private static final String DEFAULT_LOG4J_CUSTOM_FORMAT = "%r [%t] %-5p %c %x - %m%n";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "UTF-8",
      label = "Charset",
      displayPosition = 300,
      group = "#0",
      dependsOn = "dataFormat^",
      triggeredByValue = {"TEXT", "JSON", "DELIMITED", "XML", "LOG"}
  )
  @ValueChooserModel(CharsetChooserValues.class)
  public String charset = "UTF-8";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Ignore Control Characters",
      description = "Use only if required as it impacts reading performance",
      displayPosition = 310,
      group = "#0"
  )
  public boolean removeCtrlChars = false;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      description = "Compression formats gzip, bzip2, xz, lzma, Pack200, DEFLATE and Z are supported. " +
          "Archive formats 7z, ar, arj, cpio, dump, tar and zip are supported.",
      defaultValue = "NONE",
      label = "Compression Format",
      displayPosition = 320,
      group = "#0"
  )
  @ValueChooserModel(CompressionChooserValues.class)
  public Compression compression = Compression.NONE;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      label = "File Name Pattern within Compressed Directory",
      description = "A glob or regular expression that defines the pattern of the file names within the compressed " +
          "directory.",
      defaultValue = "*",
      displayPosition = 330,
      group = "#0",
      dependsOn = "compression",
      triggeredByValue = {"ARCHIVE", "COMPRESSED_ARCHIVE"}
  )
  public String filePatternInArchive = "*";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "1024",
      label = "Max Line Length",
      description = "Longer lines are truncated",
      displayPosition = 340,
      group = "TEXT",
      dependsOn = "dataFormat^",
      triggeredByValue = "TEXT",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int textMaxLineLen = 1024;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "MULTIPLE_OBJECTS",
      label = "JSON Content",
      description = "",
      displayPosition = 350,
      group = "JSON",
      dependsOn = "dataFormat^",
      triggeredByValue = "JSON"
  )
  @ValueChooserModel(JsonModeChooserValues.class)
  public JsonMode jsonContent = JsonMode.MULTIPLE_OBJECTS;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "4096",
      label = "Max Object Length (chars)",
      description = "Larger objects are not processed",
      displayPosition = 360,
      group = "JSON",
      dependsOn = "dataFormat^",
      triggeredByValue = "JSON",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int jsonMaxObjectLen = 4096;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "CSV",
      label = "Delimiter Format Type",
      description = "",
      displayPosition = 370,
      group = "DELIMITED",
      dependsOn = "dataFormat^",
      triggeredByValue = "DELIMITED"
  )
  @ValueChooserModel(CsvModeChooserValues.class)
  public CsvMode csvFileFormat = CsvMode.CSV;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "NO_HEADER",
      label = "Header Line",
      description = "",
      displayPosition = 380,
      group = "DELIMITED",
      dependsOn = "dataFormat^",
      triggeredByValue = "DELIMITED"
  )
  @ValueChooserModel(CsvHeaderChooserValues.class)
  public CsvHeader csvHeader = CsvHeader.NO_HEADER;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "1024",
      label = "Max Record Length (chars)",
      description = "Larger objects are not processed",
      displayPosition = 390,
      group = "DELIMITED",
      dependsOn = "dataFormat^",
      triggeredByValue = "DELIMITED",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int csvMaxObjectLen = 1024;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.CHARACTER,
      defaultValue = "|",
      label = "Delimiter Character",
      displayPosition = 400,
      group = "DELIMITED",
      dependsOn = "csvFileFormat",
      triggeredByValue = "CUSTOM"
  )
  public char csvCustomDelimiter = '|';

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.CHARACTER,
      defaultValue = "\\",
      label = "Escape Character",
      displayPosition = 410,
      group = "DELIMITED",
      dependsOn = "csvFileFormat",
      triggeredByValue = "CUSTOM"
  )
  public char csvCustomEscape = '\\';

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.CHARACTER,
      defaultValue = "\"",
      label = "Quote Character",
      displayPosition = 420,
      group = "DELIMITED",
      dependsOn = "csvFileFormat",
      triggeredByValue = "CUSTOM"
  )
  public char csvCustomQuote = '\"';

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "LIST_MAP",
      label = "Root Field Type",
      description = "",
      displayPosition = 430,
      group = "DELIMITED",
      dependsOn = "dataFormat^",
      triggeredByValue = "DELIMITED"
  )
  @ValueChooserModel(CsvRecordTypeChooserValues.class)
  public CsvRecordType csvRecordType = CsvRecordType.LIST_MAP;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "0",
      label = "Start Lines to Skip",
      displayPosition = 435,
      group = "DELIMITED",
      dependsOn = "dataFormat^",
      triggeredByValue = "DELIMITED",
      min = 0
  )
  public int csvSkipStartLines;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      label = "Delimiter Element",
      defaultValue = "",
      description = "XML element that acts as a record delimiter. No delimiter will treat the whole XML document as one record.",
      displayPosition = 440,
      group = "XML",
      dependsOn = "dataFormat^",
      triggeredByValue = "XML"
  )
  public String xmlRecordElement = "";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "4096",
      label = "Max Record Length (chars)",
      description = "Larger records are not processed",
      displayPosition = 450,
      group = "XML",
      dependsOn = "dataFormat^",
      triggeredByValue = "XML",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int xmlMaxObjectLen = 4096;

  // LOG Configuration

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "COMMON_LOG_FORMAT",
      label = "Log Format",
      description = "",
      displayPosition = 460,
      group = "LOG",
      dependsOn = "dataFormat^",
      triggeredByValue = "LOG"
  )
  @ValueChooserModel(LogModeChooserValues.class)
  public LogMode logMode = LogMode.COMMON_LOG_FORMAT;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "1024",
      label = "Max Line Length",
      description = "Longer lines are truncated",
      displayPosition = 470,
      group = "LOG",
      dependsOn = "dataFormat^",
      triggeredByValue = "LOG",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int logMaxObjectLen = 1024;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Retain Original Line",
      description = "Indicates if the original line of log should be retained in the record",
      displayPosition = 480,
      group = "LOG",
      dependsOn = "dataFormat^",
      triggeredByValue = "LOG"
  )
  public boolean retainOriginalLine = false;

  //APACHE_CUSTOM_LOG_FORMAT
  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = DEFAULT_APACHE_CUSTOM_LOG_FORMAT,
      label = "Custom Log Format",
      description = "",
      displayPosition = 490,
      group = "LOG",
      dependsOn = "logMode",
      triggeredByValue = "APACHE_CUSTOM_LOG_FORMAT"
  )
  public String customLogFormat = DEFAULT_APACHE_CUSTOM_LOG_FORMAT;

  //REGEX

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = DEFAULT_REGEX,
      label = "Regular Expression",
      description = "The regular expression which is used to parse the log line.",
      displayPosition = 500,
      group = "LOG",
      dependsOn = "logMode",
      triggeredByValue = "REGEX"
  )
  public String regex = DEFAULT_REGEX;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "",
      label = "Field Path To RegEx Group Mapping",
      description = "Map groups in the regular expression to field paths",
      displayPosition = 510,
      group = "LOG",
      dependsOn = "logMode",
      triggeredByValue = "REGEX"
  )
  @ListBeanModel
  public List<RegExConfig> fieldPathsToGroupName = new ArrayList<>();

  //GROK

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.TEXT,
      defaultValue = "",
      label = "Grok Pattern Definition",
      description = "Define your own grok patterns which will be used to parse the logs",
      displayPosition = 520,
      group = "LOG",
      dependsOn = "logMode",
      triggeredByValue = "GROK",
      mode = ConfigDef.Mode.PLAIN_TEXT
  )
  public String grokPatternDefinition = "";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = DEFAULT_GROK_PATTERN,
      label = "Grok Pattern",
      description = "The grok pattern which is used to parse the log line",
      displayPosition = 530,
      group = "LOG",
      dependsOn = "logMode",
      triggeredByValue = "GROK"
  )
  public String grokPattern = DEFAULT_GROK_PATTERN;

  //LOG4J

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "ERROR",
      label = "On Parse Error",
      description = "",
      displayPosition = 540,
      group = "LOG",
      dependsOn = "logMode",
      triggeredByValue = "LOG4J"
  )
  @ValueChooserModel(OnParseErrorChooserValues.class)
  public OnParseError onParseError = OnParseError.ERROR;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "50",
      label = "Trim Stack Trace to Length",
      description = "Any line that does not match the expected pattern will be treated as a Stack trace as long as it " +
          "is part of the same message. The stack trace will be trimmed to the specified number of lines.",
      displayPosition = 550,
      group = "LOG",
      dependsOn = "onParseError",
      triggeredByValue = "INCLUDE_AS_STACK_TRACE",
      min = 0,
      max = Integer.MAX_VALUE
  )
  public int maxStackTraceLines = 50;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Use Custom Log Format",
      description = "",
      displayPosition = 560,
      group = "LOG",
      dependsOn = "logMode",
      triggeredByValue = "LOG4J"
  )
  public boolean enableLog4jCustomLogFormat = false;


  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = DEFAULT_LOG4J_CUSTOM_FORMAT,
      label = "Custom Log4J Format",
      description = "Specify your own custom log4j format.",
      displayPosition = 570,
      group = "LOG",
      dependsOn = "enableLog4jCustomLogFormat",
      triggeredByValue = "true"
  )
  public String log4jCustomLogFormat = DEFAULT_LOG4J_CUSTOM_FORMAT;

  //AVRO

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "true",
      label = "Message includes Schema",
      description = "The Kafka message includes the Avro schema",
      displayPosition = 580,
      group = "AVRO",
      dependsOn = "dataFormat^",
      triggeredByValue = "AVRO"
  )
  public boolean schemaInMessage = true;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.TEXT,
      defaultValue = "",
      label = "Avro Schema",
      description = "Overrides the schema associated with the message. Optionally use the runtime:loadResource function to use a schema stored in a file",
      displayPosition = 590,
      group = "AVRO",
      dependsOn = "dataFormat^",
      triggeredByValue = "AVRO",
      mode = ConfigDef.Mode.JSON
  )
  public String avroSchema = "";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Protobuf Descriptor File",
      description = "Protobuf Descriptor File (.desc) path relative to SDC resources directory",
      displayPosition = 600,
      group = "PROTOBUF",
      dependsOn = "dataFormat^",
      triggeredByValue = "PROTOBUF"
  )
  public String protoDescriptorFile = "";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Message Type",
      displayPosition = 610,
      group = "PROTOBUF",
      dependsOn = "dataFormat^",
      triggeredByValue = "PROTOBUF"
  )
  public String messageType = "";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "true",
      label = "Delimited Messages",
      description = "Should be checked when the input data is prepended with the message size. When unchecked " +
          "only a single message can be present in the source file/Kafka message, etc.",
      displayPosition = 620,
      group = "PROTOBUF",
      dependsOn = "dataFormat^",
      triggeredByValue = "PROTOBUF"
  )
  public boolean isDelimited = true;

  public boolean init(Stage.Context context, DataFormat dataFormat, String stageGroup, List<Stage.ConfigIssue> issues) {
    return init(context, dataFormat, stageGroup, DataFormatConstants.MAX_OVERRUN_LIMIT, issues);
  }

  public boolean init(Stage.Context context, DataFormat dataFormat, String stageGroup, int overrunLimit, List<Stage.ConfigIssue> issues) {
    boolean valid = true;
    switch (dataFormat) {
      case JSON:
        if (jsonMaxObjectLen < 1) {
          issues.add(context.createConfigIssue(DataFormatGroups.JSON.name(), "jsonMaxObjectLen",
              DataFormatErrors.DATA_FORMAT_01));
          valid = false;
        }
        break;
      case TEXT:
        if (textMaxLineLen < 1) {
          issues.add(context.createConfigIssue(DataFormatGroups.TEXT.name(), "textMaxLineLen",
              DataFormatErrors.DATA_FORMAT_01));
          valid = false;
        }
        break;
      case DELIMITED:
        if (csvMaxObjectLen < 1) {
          issues.add(context.createConfigIssue(DataFormatGroups.DELIMITED.name(), "csvMaxObjectLen",
              DataFormatErrors.DATA_FORMAT_01));
          valid = false;
        }
        break;
      case XML:
        if (xmlMaxObjectLen < 1) {
          issues.add(context.createConfigIssue(DataFormatGroups.XML.name(), "xmlMaxObjectLen",
              DataFormatErrors.DATA_FORMAT_01));
          valid = false;
        }
        if (xmlRecordElement != null && !xmlRecordElement.isEmpty() && !XMLChar.isValidName(xmlRecordElement)) {
          issues.add(
              context.createConfigIssue(DataFormatGroups.XML.name(),
                  "xmlRecordElement",
                  DataFormatErrors.DATA_FORMAT_03,
                  xmlRecordElement
              )
          );
          valid = false;
        }
        break;
      case SDC_JSON:
        break;
      case AVRO:
        break;
      case LOG:
        logDataFormatValidator = new LogDataFormatValidator(
            logMode,
            logMaxObjectLen,
            retainOriginalLine,
            customLogFormat,
            regex,
            grokPatternDefinition,
            grokPattern,
            enableLog4jCustomLogFormat,
            log4jCustomLogFormat,
            onParseError,
            maxStackTraceLines,
            DataFormatGroups.LOG.name(),
            getFieldPathToGroupMap(fieldPathsToGroupName)
        );
        logDataFormatValidator.validateLogFormatConfig(issues, context);
        break;
      case PROTOBUF:
        if (protoDescriptorFile == null || protoDescriptorFile.isEmpty()) {
          issues.add(
              context.createConfigIssue(
                  DataFormatGroups.PROTOBUF.name(),
                  "protoDescriptorFile",
                  DataFormatErrors.DATA_FORMAT_07
              )
          );
        } else {
          File file = new File(context.getResourcesDirectory(), protoDescriptorFile);
          if (!file.exists()) {
            issues.add(
                context.createConfigIssue(
                    DataFormatGroups.PROTOBUF.name(),
                    "protoDescriptorFile",
                    DataFormatErrors.DATA_FORMAT_09,
                    file.getAbsolutePath()
                )
            );
          }
          if (messageType == null || messageType.isEmpty()) {
            issues.add(
                context.createConfigIssue(
                    DataFormatGroups.PROTOBUF.name(),
                    "messageType",
                    DataFormatErrors.DATA_FORMAT_08
                )
            );
          }
        }
        break;
      default:
        issues.add(context.createConfigIssue(stageGroup, "dataFormat", DataFormatErrors.DATA_FORMAT_04, dataFormat));
        valid = false;
        break;
    }

    valid &= validateDataParser(context, dataFormat, stageGroup, overrunLimit, issues);

    return valid;
  }

  private boolean validateDataParser(
      Stage.Context context,
      DataFormat dataFormat,
      String stageGroup,
      int overrunLimit,
      List<Stage.ConfigIssue> issues
  ) {
    boolean valid = true;
    DataParserFactoryBuilder builder = new DataParserFactoryBuilder(context, dataFormat.getParserFormat());
    Charset fileCharset;

    try {
      fileCharset = Charset.forName(charset);
    } catch (UnsupportedCharsetException ex) {
      // setting it to a valid one so the parser factory can be configured and tested for more errors
      fileCharset = StandardCharsets.UTF_8;
      issues.add(context.createConfigIssue(stageGroup, "charset", DataFormatErrors.DATA_FORMAT_05, charset));
      valid = false;
    }
    builder.setCharset(fileCharset);
    builder.setOverRunLimit(overrunLimit);
    builder.setRemoveCtrlChars(removeCtrlChars);
    builder.setCompression(compression);
    builder.setFilePatternInArchive(filePatternInArchive);

    switch (dataFormat) {
      case TEXT:
        builder.setMaxDataLen(textMaxLineLen);
        break;
      case JSON:
        builder.setMaxDataLen(jsonMaxObjectLen).setMode(jsonContent);
        break;
      case DELIMITED:
        builder
            .setMaxDataLen(csvMaxObjectLen)
            .setMode(csvFileFormat).setMode(csvHeader)
            .setMode(csvRecordType)
            .setConfig(DelimitedDataConstants.SKIP_START_LINES, csvSkipStartLines)
            .setConfig(DelimitedDataConstants.DELIMITER_CONFIG, csvCustomDelimiter)
            .setConfig(DelimitedDataConstants.ESCAPE_CONFIG, csvCustomEscape)
            .setConfig(DelimitedDataConstants.QUOTE_CONFIG, csvCustomQuote);
        break;
      case XML:
        builder.setMaxDataLen(xmlMaxObjectLen).setConfig(XmlDataParserFactory.RECORD_ELEMENT_KEY, xmlRecordElement);
        break;
      case SDC_JSON:
        builder.setMaxDataLen(-1);
        break;
      case LOG:
        logDataFormatValidator.populateBuilder(builder);
        break;
      case AVRO:
        builder.setMaxDataLen(-1).setConfig(AvroDataParserFactory.SCHEMA_KEY, avroSchema);
        break;
      case PROTOBUF:
        builder
            .setConfig(ProtobufConstants.PROTO_DESCRIPTOR_FILE_KEY, protoDescriptorFile)
            .setConfig(ProtobufConstants.MESSAGE_TYPE_KEY, messageType)
            .setConfig(ProtobufConstants.DELIMITED_KEY, isDelimited)
            .setMaxDataLen(-1);
        break;
      default:
        throw new IllegalStateException("Unexpected data format" + dataFormat);
    }
    try {
      parserFactory = builder.build();
    } catch (Exception ex) {
      issues.add(context.createConfigIssue(null, null, DataFormatErrors.DATA_FORMAT_06, ex.toString(), ex));
      valid = false;
    }
    return valid;
  }

  public DataParserFactory getParserFactory() {
    return parserFactory;
  }

  private LogDataFormatValidator logDataFormatValidator;
  private DataParserFactory parserFactory;

  private Map<String, Integer> getFieldPathToGroupMap(List<RegExConfig> fieldPathsToGroupName) {
    if (fieldPathsToGroupName == null) {
      return new HashMap<>();
    }
    Map<String, Integer> fieldPathToGroup = new HashMap<>();
    for (RegExConfig r : fieldPathsToGroupName) {
      fieldPathToGroup.put(r.fieldPath, r.group);
    }
    return fieldPathToGroup;
  }
}
