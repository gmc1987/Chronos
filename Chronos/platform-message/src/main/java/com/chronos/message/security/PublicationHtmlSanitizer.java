package com.chronos.message.security;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 通知公告富文本白名单清洗器。
 *
 * 所有富文本在写入数据库之前完成清洗，门户端只渲染清洗后的 HTML，
 * 从源头阻止脚本、事件属性、危险协议和嵌入对象形成存储型 XSS。
 */
@Component
public class PublicationHtmlSanitizer {

	private final Safelist safelist = Safelist.relaxed()
			.addTags("h1", "h2", "h3", "h4", "h5", "h6", "hr")
			.addAttributes("a", "target", "rel")
			.addAttributes("table", "border", "cellpadding", "cellspacing")
			.addProtocols("a", "href", "http", "https", "mailto")
			.addProtocols("img", "src", "http", "https");

	public String sanitize(String html) {
		if (html == null || html.isBlank()) {
			return "";
		}
		Document dirty = Jsoup.parseBodyFragment(html);
		Document clean = new Cleaner(safelist).clean(dirty);
		clean.outputSettings().prettyPrint(false);
		clean.select("a[target=_blank]").attr("rel", "noopener noreferrer");
		return clean.body().html();
	}

	public String textAsHtml(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		Document document = Document.createShell("");
		for (String paragraph : text.split("\\R", -1)) {
			document.body().appendElement("p").text(paragraph.isBlank() ? " " : paragraph);
		}
		return sanitize(document.body().html());
	}
}
