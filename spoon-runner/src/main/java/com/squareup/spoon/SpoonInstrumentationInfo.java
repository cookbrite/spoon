package com.squareup.spoon;

import com.squareup.spoon.axmlparser.AXMLParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static com.google.common.base.Preconditions.checkNotNull;

/** Detailed instrumentation information. */
final class SpoonInstrumentationInfo {
  private final String applicationPackage;
  private final String instrumentationPackage;
  private final String testRunnerClass;

  private int totalNodes = 1;
  private int currentNode = 1;
  private int batchSize = 5;
  private List<TestClass> totalClasses = null;

  SpoonInstrumentationInfo(String applicationPackage, String instrumentationPackage,
      String testRunnerClass, List<TestClass> tc, int tn, int cn, int bs) {
    this.applicationPackage = applicationPackage;
    this.instrumentationPackage = instrumentationPackage;
    this.testRunnerClass = testRunnerClass;
    this.totalClasses = tc;
    this.totalNodes = tn;
    this.currentNode = cn;
    this.batchSize = bs;
  }

  String getApplicationPackage() {
    return applicationPackage;
  }

  String getInstrumentationPackage() {
    return instrumentationPackage;
  }

  String getTestRunnerClass() {
    return testRunnerClass;
  }

  List<TestClass> getAllTestClasses() {
    return totalClasses;
  }

  String[] getAllTestClassNames() {
    if (totalClasses != null && totalClasses.size() > 0) {
      String[] ret = new String[totalClasses.size()];
      for (int i = 0; i < totalClasses.size(); i++) {
        TestClass t = totalClasses.get(i);
        ret[i] = t.getClassName();
      }
      return ret;
    }
    return null;
  }

  int getTotalNodes() {
    return totalNodes;
  }

  int getCurrentNode() {
    return currentNode;
  }

  int getBatchSize() {return batchSize;}

  @Override public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  /** Parse key information from an instrumentation APK's manifest. */
  static SpoonInstrumentationInfo parseFromFile(File apkTestFile, File output) {
    return parseFromFile(apkTestFile, output, 1, 1, 5, null);
  }

  static SpoonInstrumentationInfo parseFromFile(File apkTestFile, File output,
                                                int totalNodes, int currentNode, int batchSize,
                                                String filterPatterns) {
    InputStream is = null;
    try {
      List<TestClass> testClasses = null;
      if (totalNodes > 1 || !StringUtils.isEmpty(filterPatterns)) {
        SpoonLogger.logInfo("loading test classes from %s", apkTestFile.toPath());

        testClasses = (new TestClassScanner(apkTestFile, output))
          .scanForTestClasses();
        TestClassFilter filter = new TestClassFilter(filterPatterns);
        testClasses = filter.anyUserFilter(testClasses);
        Collections.sort(testClasses);
        SpoonLogger.logInfo("loaded %d classes", testClasses.size());
        testClasses = filterByNodeIndex(testClasses, totalNodes, currentNode);
        SpoonLogger.logInfo("Filtered down to %d classes", testClasses.size());
      } else {
        SpoonLogger.logInfo("Not filtering test classes");
      }

      ZipFile zip = new ZipFile(apkTestFile);
      ZipEntry entry = zip.getEntry("AndroidManifest.xml");
      is = zip.getInputStream(entry);

      AXMLParser parser = new AXMLParser(is);
      int eventType = parser.getType();

      String appPackage = null;
      String testPackage = null;
      String testRunnerClass = null;
      while (eventType != AXMLParser.END_DOCUMENT) {
        if (eventType == AXMLParser.START_TAG) {
          String parserName = parser.getName();
          boolean isManifest = "manifest".equals(parserName);
          boolean isInstrumentation = "instrumentation".equals(parserName);
          if (isManifest || isInstrumentation) {
            for (int i = 0; i < parser.getAttributeCount(); i++) {
              String parserAttributeName = parser.getAttributeName(i);
              if (isManifest && "package".equals(parserAttributeName)) {
                testPackage = parser.getAttributeValueString(i);
              } else if (isInstrumentation && "targetPackage".equals(parserAttributeName)) {
                appPackage = parser.getAttributeValueString(i);
              } else if (isInstrumentation && "name".equals(parserAttributeName)) {
                testRunnerClass = parser.getAttributeValueString(i);
              }
            }
          }
        }
        eventType = parser.next();
      }
      checkNotNull(testPackage, "Could not find test application package.");
      checkNotNull(appPackage, "Could not find application package.");
      checkNotNull(testRunnerClass, "Could not find test runner class.");

      // Support relative declaration of instrumentation test runner.
      if (testRunnerClass.startsWith(".")) {
        testRunnerClass = testPackage + testRunnerClass;
      } else if (!testRunnerClass.contains(".")) {
        testRunnerClass = testPackage + "." + testRunnerClass;
      }

      return new SpoonInstrumentationInfo(appPackage, testPackage, testRunnerClass, testClasses,
              totalNodes, currentNode, batchSize);
    } catch (IOException e) {
      throw new RuntimeException("Unable to parse test app AndroidManifest.xml.", e);
    } finally {
      IOUtils.closeQuietly(is);
    }
  }

  static List<TestClass> filterByNodeIndex(List<TestClass> list, int total, int cur) {
    if (total == 1) {
      return list;
    }

    List<TestClass> ret = new ArrayList<TestClass>(list.size());
    for (int i = 0; i < list.size(); i++) {
      if (i % total == cur) {
        ret.add(list.get(i));
      }
    }
    return ret;
  }
}
