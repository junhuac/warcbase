package org.warcbase.ingest;

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.warc.WARCReader;
import org.archive.io.warc.WARCReaderFactory;
import org.archive.io.warc.WARCRecord;
import org.archive.util.ArchiveUtils;
import org.junit.Test;
import org.warcbase.data.WarcRecordUtils;

import tl.lin.data.fd.Object2IntFrequencyDistribution;
import tl.lin.data.fd.Object2IntFrequencyDistributionEntry;

import com.google.common.io.Resources;

public class WacWarcLoaderTest {
  private static final Log LOG = LogFactory.getLog(WacWarcLoaderTest.class);
  private static final SimpleDateFormat DATE_WARC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

  @Test
  public void testReader() throws Exception {
    String warcFile = Resources.getResource("warc/example.warc.gz").getPath();
    WARCReader reader = WARCReaderFactory.get(new File(warcFile));

    Object2IntFrequencyDistribution<String> types =
        new Object2IntFrequencyDistributionEntry<String>();

    int cnt = 0;
    for (Iterator<ArchiveRecord> ii = reader.iterator(); ii.hasNext();) {
      WARCRecord r = (WARCRecord) ii.next();
      ArchiveRecordHeader header = r.getHeader();

      types.increment((String) header.getHeaderValue("WARC-Type"));

      byte[] contents = WarcRecordUtils.getContent(r);
      int len = (int) header.getContentLength();
      assertEquals(len, contents.length);

      // This is how you extract the date
      @SuppressWarnings("unused")
      String digit14Date = ArchiveUtils.get14DigitDate(DATE_WARC.parse(header.getDate()));

      // This is a somewhat janky way to get the MIME type of the response.
      // Note that this is different from the "Content-Type" in the WARC header.
      if (header.getHeaderValue("WARC-Type").equals("response") &&
          header.getUrl().startsWith("http://")) {
        Pattern pattern = Pattern.compile("Content-Type: ([^\\s]+)");
        Matcher matcher = pattern.matcher(new String(contents));
        if (matcher.find()) {
          @SuppressWarnings("unused")
          String mimeType = matcher.group(1).replaceAll(";$", "");
        }
      }

      cnt++;
    }
    reader.close();

    LOG.info(cnt + " records read!");
    assertEquals(822, cnt);

    assertEquals(299, types.get("response"));
    assertEquals(1, types.get("warcinfo"));
    assertEquals(261, types.get("request"));
    assertEquals(261, types.get("metadata"));
    assertEquals(4, types.getNumberOfEvents());
    assertEquals(822, types.getSumOfCounts());
  }

  @Test
  public void testReadFromStream() throws Exception {
    String warcFile = Resources.getResource("warc/example.warc.gz").getPath();
    WARCReader reader = WARCReaderFactory.get(new File(warcFile));

    int cnt = 0;
    for (Iterator<ArchiveRecord> ii = reader.iterator(); ii.hasNext();) {
      WARCRecord r = (WARCRecord) ii.next();
      InputStream in = new DataInputStream(new ByteArrayInputStream(WarcRecordUtils.toBytes(r)));

      WARCReader nr = (WARCReader) WARCReaderFactory.get("",
          new BufferedInputStream(in), false);
      WARCRecord r2 = (WARCRecord) nr.get();

      assertEquals(r.getHeader().getUrl(), r2.getHeader().getUrl());

      ArchiveRecordHeader header = r2.getHeader();
      byte[] contents = WarcRecordUtils.getContent(r2);
      int len = (int) header.getContentLength();
      assertEquals(len, contents.length);

      cnt++;
    }
    reader.close();

    LOG.info(cnt + " records read!");
    assertEquals(822, cnt);
  }
}
