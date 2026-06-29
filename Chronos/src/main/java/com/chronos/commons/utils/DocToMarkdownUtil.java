 package com.chronos.commons.utils;
 
 import java.io.IOException;
 import java.io.InputStream;
 import java.util.Arrays;
 import java.util.List;
 import java.util.stream.Collectors;
 import org.apache.poi.hwpf.HWPFDocument;
 import org.apache.poi.hwpf.extractor.WordExtractor;
 import org.apache.poi.xwpf.usermodel.XWPFDocument;
 import org.apache.poi.xwpf.usermodel.XWPFParagraph;
 
 
 
 public class DocToMarkdownUtil
 {
   public static String convert(InputStream inputStream, String filename) throws IOException {
     if (filename == null) throw new IOException("filename required"); 
     String lower = filename.toLowerCase();
     if (lower.endsWith(".docx")) {
       return convertDocx(inputStream);
     }
     if (lower.endsWith(".doc")) {
       return convertDoc(inputStream);
     }
     throw new IOException("unsupported file type");
   }
   
   private static String convertDoc(InputStream inputStream) throws IOException {
     HWPFDocument doc = new HWPFDocument(inputStream); try { WordExtractor extractor = new WordExtractor(doc); 
       try { String[] paragraphs = extractor.getParagraphText();
         if (paragraphs == null) { String str1 = "";
 
 
 
           
           extractor.close(); doc.close(); return str1; }  String str = Arrays.<String>stream(paragraphs).map(p -> (p == null) ? "" : p.trim()).filter(p -> !p.isEmpty()).collect(Collectors.joining("\n\n")); extractor.close(); doc.close(); return str; } catch (Throwable throwable) { try { extractor.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }  throw throwable; }  }
     catch (Throwable throwable) { try { doc.close(); }
       catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
        throw throwable; }
      } private static String convertDocx(InputStream inputStream) throws IOException { XWPFDocument doc = new XWPFDocument(inputStream); try {
       List<XWPFParagraph> paragraphs = doc.getParagraphs();
       if (paragraphs == null) { String str1 = "";
 
 
 
 
         
         doc.close(); return str1; }  String str = paragraphs.stream().map(p -> (p == null) ? "" : p.getText()).map(t -> (t == null) ? "" : t.trim()).filter(t -> !t.isEmpty()).collect(Collectors.joining("\n\n")); doc.close();
       return str;
     } catch (Throwable throwable) {
       try {
         doc.close();
       } catch (Throwable throwable1) {
         throwable.addSuppressed(throwable1);
       } 
       throw throwable;
     }  }
 
 }


