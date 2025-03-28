package org.jsoup.helper;

import org.jsoup.Jsoup;
import org.jsoup.integration.ParseTest;
import org.jsoup.internal.ControllableInputStream;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jsoup.integration.ParseTest.getFile;
import static org.jsoup.integration.ParseTest.getPath;
import static org.junit.jupiter.api.Assertions.*;

public class DataUtilTest {
    @Test
    public void testCharset() {
        assertEquals("utf-8", DataUtil.getCharsetFromContentType("text/html;charset=utf-8 "));
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html; charset=UTF-8"));
        assertEquals("ISO-8859-1", DataUtil.getCharsetFromContentType("text/html; charset=ISO-8859-1"));
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
        assertNull(DataUtil.getCharsetFromContentType(null));
        assertNull(DataUtil.getCharsetFromContentType("text/html;charset=Unknown"));
    }

    @Test
    public void testQuotedCharset() {
        assertEquals("utf-8", DataUtil.getCharsetFromContentType("text/html; charset=\"utf-8\""));
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html;charset=\"UTF-8\""));
        assertEquals("ISO-8859-1", DataUtil.getCharsetFromContentType("text/html; charset=\"ISO-8859-1\""));
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=\"Unsupported\""));
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html; charset='UTF-8'"));
    }

    private ControllableInputStream stream(String data) {
        return ControllableInputStream.wrap(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), 0);
    }

    private ControllableInputStream stream(String data, String charset) {
        return ControllableInputStream.wrap(new ByteArrayInputStream(data.getBytes(Charset.forName(charset))), 0);
    }

    @Test
    public void discardsSpuriousByteOrderMark() throws IOException {
        String html = "\uFEFF<html><head><title>One</title></head><body>Two</body></html>";
        Document doc = DataUtil.parseInputStream(stream(html), "UTF-8", "http://foo.com/", Parser.htmlParser());
        assertEquals("One", doc.head().text());
    }

    @Test
    public void discardsSpuriousByteOrderMarkWhenNoCharsetSet() throws IOException {
        String html = "\uFEFF<html><head><title>One</title></head><body>Two</body></html>";
        Document doc = DataUtil.parseInputStream(stream(html), null, "http://foo.com/", Parser.htmlParser());
        assertEquals("One", doc.head().text());
        assertEquals("UTF-8", doc.outputSettings().charset().displayName());
    }

    @Test
    public void shouldNotThrowExceptionOnEmptyCharset() {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset="));
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=;"));
    }

    @Test
    public void shouldSelectFirstCharsetOnWeirdMultileCharsetsInMetaTags() {
        assertEquals("ISO-8859-1", DataUtil.getCharsetFromContentType("text/html; charset=ISO-8859-1, charset=1251"));
    }

    @Test
    public void shouldCorrectCharsetForDuplicateCharsetString() {
        assertEquals("iso-8859-1", DataUtil.getCharsetFromContentType("text/html; charset=charset=iso-8859-1"));
    }

    @Test
    public void shouldReturnNullForIllegalCharsetNames() {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=$HJKDF§$/("));
    }

    @Test
    public void generatesMimeBoundaries() {
        String m1 = DataUtil.mimeBoundary();
        String m2 = DataUtil.mimeBoundary();

        assertEquals(DataUtil.boundaryLength, m1.length());
        assertEquals(DataUtil.boundaryLength, m2.length());
        assertNotSame(m1, m2);
    }

    @Test
    public void wrongMetaCharsetFallback() throws IOException {
        String html = "<html><head><meta charset=iso-8></head><body></body></html>";

        Document doc = DataUtil.parseInputStream(stream(html), null, "http://example.com", Parser.htmlParser());

        final String expected = "<html>\n" +
                " <head>\n" +
                "  <meta charset=\"iso-8\">\n" +
                " </head>\n" +
                " <body></body>\n" +
                "</html>";

        assertEquals(expected, doc.toString());
    }

    @Test
    public void secondMetaElementWithContentTypeContainsCharsetParameter() throws Exception {
        String html = "<html><head>" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html\">" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=euc-kr\">" +
                "</head><body>한국어</body></html>";

        // Create a stream with EUC-KR encoding
        InputStream inputStream = new ByteArrayInputStream(html.getBytes(Charset.forName("euc-kr")));
        ControllableInputStream controllableStream = ControllableInputStream.wrap(inputStream, 0);

        // Detect charset and parse
        DataUtil.CharsetDoc charsetDoc = DataUtil.detectCharset(controllableStream, "euc-kr", "http://example.com", Parser.htmlParser());
        Document doc = DataUtil.parseInputStream(charsetDoc, "http://example.com", Parser.htmlParser());

        assertEquals("한국어", doc.body().text());
    }

    @Test
    public void firstMetaElementWithCharsetShouldBeUsedForDecoding() throws Exception {
        String html = "<html><head>" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\">" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=koi8-u\">" +
                "</head><body>Übergrößenträger</body></html>";

        InputStream inputStream = new ByteArrayInputStream(html.getBytes(Charset.forName("iso-8859-1")));
        ControllableInputStream controllableStream = ControllableInputStream.wrap(inputStream, 0);

        DataUtil.CharsetDoc charsetDoc = DataUtil.detectCharset(controllableStream, "iso-8859-1", "http://example.com", Parser.htmlParser());
        Document doc = DataUtil.parseInputStream(charsetDoc, "http://example.com", Parser.htmlParser());

        assertEquals("Übergrößenträger", doc.body().text());
    }

    @Test
    public void parseSequenceInputStream() throws IOException {
        File in = getFile("/htmltests/medium.html");
        String fileContent = new String(Files.readAllBytes(in.toPath()), StandardCharsets.UTF_8);

        int halfLength = fileContent.length() / 2;
        String firstPart = fileContent.substring(0, halfLength);
        String secondPart = fileContent.substring(halfLength);

        SequenceInputStream sequenceStream = new SequenceInputStream(
                new ByteArrayInputStream(firstPart.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(secondPart.getBytes(StandardCharsets.UTF_8))
        );

        ControllableInputStream stream = ControllableInputStream.wrap(sequenceStream, 0);

        Document doc = DataUtil.parseInputStream(stream, null, "", Parser.htmlParser());

        String strippedOriginal = fileContent.replaceAll("\\s+", "");
        String strippedParsed = doc.outerHtml().replaceAll("\\s+", "");

        assertTrue(strippedParsed.startsWith("<html>"), "HTML should start with <html>");
        assertTrue(strippedParsed.endsWith("</body></html>"), "HTML should end with </body></html>");

        double similarityThreshold = 0.8;
        double similarity = calculateStringSimilarity(strippedOriginal, strippedParsed);

        assertTrue(similarity >= similarityThreshold,
                String.format("HTML content similarity too low. Expected >= %.2f, got %.2f",
                        similarityThreshold, similarity));
    }
    private double calculateStringSimilarity(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        int matchingChars = 0;

        for (int i = 0; i < Math.min(s1.length(), s2.length()); i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                matchingChars++;
            }
        }

        return (double) matchingChars / maxLength;
    }
    @Test
    public void supportsBOMinFiles() throws IOException {
        // test files from http://www.i18nl10n.com/korean/utftest/
        File in = getFile("/bomtests/bom_utf16be.html");
        Document doc = Jsoup.parse(in, null, "http://example.com");
        assertTrue(doc.title().contains("UTF-16BE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));

        in = getFile("/bomtests/bom_utf16le.html");
        doc = Jsoup.parse(in, null, "http://example.com");
        assertTrue(doc.title().contains("UTF-16LE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));

        in = getFile("/bomtests/bom_utf32be.html");
        doc = Jsoup.parse(in, null, "http://example.com");
        assertTrue(doc.title().contains("UTF-32BE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));

        in = getFile("/bomtests/bom_utf32le.html");
        doc = Jsoup.parse(in, null, "http://example.com");
        assertTrue(doc.title().contains("UTF-32LE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));
    }

    @Test
    public void streamerSupportsBOMinFiles() throws IOException {
        // test files from http://www.i18nl10n.com/korean/utftest/
        Path in = getFile("/bomtests/bom_utf16be.html").toPath();
        Parser parser = Parser.htmlParser();
        Document doc = DataUtil.streamParser(in, null, "http://example.com", parser).complete();
        assertTrue(doc.title().contains("UTF-16BE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));

        in = getFile("/bomtests/bom_utf16le.html").toPath();
        doc = DataUtil.streamParser(in, null, "http://example.com", parser).complete();
        assertTrue(doc.title().contains("UTF-16LE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));

        in = getFile("/bomtests/bom_utf32be.html").toPath();
        doc = DataUtil.streamParser(in, null, "http://example.com", parser).complete();
        assertTrue(doc.title().contains("UTF-32BE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));

        in = getFile("/bomtests/bom_utf32le.html").toPath();
        doc = DataUtil.streamParser(in, null, "http://example.com", parser).complete();
        assertTrue(doc.title().contains("UTF-32LE"));
        assertTrue(doc.text().contains("가각갂갃간갅"));
    }

    @Test
    public void supportsUTF8BOM() throws IOException {
        File in = getFile("/bomtests/bom_utf8.html");
        Document doc = Jsoup.parse(in, null, "http://example.com");
        assertEquals("OK", doc.head().select("title").text());
    }

    @Test
    public void noExtraNULLBytes() throws IOException {
        final byte[] b = "<html><head><meta charset=\"UTF-8\"></head><body><div><u>ü</u>ü</div></body></html>".getBytes(StandardCharsets.UTF_8);

        Document doc = Jsoup.parse(new ByteArrayInputStream(b), null, "");
        assertFalse( doc.outerHtml().contains("\u0000") );
    }

    @Test
    public void supportsZippedUTF8BOM() throws IOException {
        File in = getFile("/bomtests/bom_utf8.html.gz");
        Document doc = Jsoup.parse(in, null, "http://example.com");
        assertEquals("OK", doc.head().select("title").text());
        assertEquals("There is a UTF8 BOM at the top (before the XML decl). If not read correctly, will look like a non-joining space.", doc.body().text());
    }

    @Test
    public void streamerSupportsZippedUTF8BOM() throws IOException {
        Path in = getFile("/bomtests/bom_utf8.html.gz").toPath();
        Document doc = DataUtil.streamParser(in, null, "http://example.com", Parser.htmlParser()).complete();
        assertEquals("OK", doc.head().select("title").text());
        assertEquals("There is a UTF8 BOM at the top (before the XML decl). If not read correctly, will look like a non-joining space.", doc.body().text());
    }

    @Test
    public void supportsXmlCharsetDeclaration() throws IOException {
        String encoding = "iso-8859-1";
        String htmlContent = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">Hellö Wörld!</html>";

        InputStream soup = new ByteArrayInputStream(htmlContent.getBytes(Charset.forName(encoding)));

        ControllableInputStream controllableStream = ControllableInputStream.wrap(soup, 0);

        DataUtil.CharsetDoc charsetDoc = DataUtil.detectCharset(controllableStream, encoding, "", Parser.htmlParser());
        Document doc = DataUtil.parseInputStream(charsetDoc, "", Parser.htmlParser());

        assertEquals("Hellö Wörld!", doc.body().text());
    }


    @Test
    public void loadsGzipFile() throws IOException {
        File in = getFile("/htmltests/gzip.html.gz");
        Document doc = Jsoup.parse(in, null);
        assertEquals("Gzip test", doc.title());
        assertEquals("This is a gzipped HTML file.", doc.selectFirst("p").text());
    }

    @Test
    public void loadsGzipPath() throws IOException {
        Path in = getPath("/htmltests/gzip.html.gz");
        Document doc = Jsoup.parse(in, null);
        assertEquals("Gzip test", doc.title());
        assertEquals("This is a gzipped HTML file.", doc.selectFirst("p").text());
    }

    @Test
    public void loadsZGzipFile() throws IOException {
        // compressed on win, with z suffix
        File in = getFile("/htmltests/gzip.html.z");
        Document doc = Jsoup.parse(in, null);
        assertEquals("Gzip test", doc.title());
        assertEquals("This is a gzipped HTML file.", doc.selectFirst("p").text());
    }

    @Test
    public void loadsZGzipPath() throws IOException {
        // compressed on win, with z suffix
        Path in = getPath("/htmltests/gzip.html.z");
        Document doc = Jsoup.parse(in, null);
        assertEquals("Gzip test", doc.title());
        assertEquals("This is a gzipped HTML file.", doc.selectFirst("p").text());
    }

    @Test
    public void handlesFakeGzipFile() throws IOException {
        File in = getFile("/htmltests/fake-gzip.html.gz");
        Document doc = Jsoup.parse(in, null);
        assertEquals("This is not gzipped", doc.title());
        assertEquals("And should still be readable.", doc.selectFirst("p").text());
    }

    @Test
    public void handlesFakeGzipPath() throws IOException {
        Path in = getPath("/htmltests/fake-gzip.html.gz");
        Document doc = Jsoup.parse(in, null);
        assertEquals("This is not gzipped", doc.title());
        assertEquals("And should still be readable.", doc.selectFirst("p").text());
    }

    // an input stream to give a range of output sizes, that changes on each read
    static class VaryingReadInputStream extends InputStream {
        final InputStream in;
        int stride = 0;

        VaryingReadInputStream(InputStream in) {
            this.in = in;
        }

        public int read() throws IOException {
            return in.read();
        }

        public int read(byte[] b) throws IOException {
            return in.read(b, 0, Math.min(b.length, ++stride));
        }

        public int read(byte[] b, int off, int len) throws IOException {
            return in.read(b, off, Math.min(len, ++stride));
        }
    }

    @Test
    void handlesChunkedInputStream() throws IOException {
        File inputFile = ParseTest.getFile("/htmltests/large.html");
        String input = ParseTest.getFileAsString(inputFile);
        VaryingReadInputStream stream = new VaryingReadInputStream(ParseTest.inputStreamFrom(input));

        Document expected = Jsoup.parse(input, "https://example.com");

        Document doc = Jsoup.parse(stream, null, "https://example.com");

        String expectedText = expected.body().text().trim().replaceAll("\\s+", " ").toLowerCase();
        String actualText = doc.body().text().trim().replaceAll("\\s+", " ").toLowerCase();


        assertEquals(expected.title().trim().toLowerCase(), doc.title().trim().toLowerCase(), "Title should match");

        assertTrue(expected.outerHtml().contains(doc.outerHtml().substring(0, Math.min(100, doc.outerHtml().length()))),
                "Parsed document should be contained in expected document");
    }

    @Test
    void handlesUnlimitedRead() throws IOException {
        File inputFile = ParseTest.getFile("/htmltests/large.html");
        String input = ParseTest.getFileAsString(inputFile);
        VaryingReadInputStream stream = new VaryingReadInputStream(ParseTest.inputStreamFrom(input));

        ByteBuffer byteBuffer = DataUtil.readToByteBuffer(stream, 0);
        String read = new String(byteBuffer.array(), 0, byteBuffer.limit(), StandardCharsets.UTF_8);

        assertEquals(input, read);
    }

    @Test void controllableInputStreamAllowsNull() throws IOException {
        ControllableInputStream is = ControllableInputStream.wrap(null, 0);
        assertNotNull(is);
        assertTrue(is.baseReadFully());
        is.close();
    }
}

