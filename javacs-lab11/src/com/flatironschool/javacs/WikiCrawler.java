package com.flatironschool.javacs;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	private Set<String> indexed = new HashSet<>();

	private static final String BASE_URL = "https://en.wikipedia.org";

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
        // FILL THIS IN!
		String url = queue.poll();
		if (url == null) {
			return null;
		}
		int queryString = url.indexOf('?');
		int anchor = url.indexOf('#');
		int min = queryString < anchor ? queryString : anchor;
		if (min != -1) {
			url = url.substring(0, min);
		}
		Elements paragraphs;
		if (testing) {
			paragraphs = wf.readWikipedia(url);
			queueInternalLinks(paragraphs);
			index.indexPage(url,paragraphs);
		} else {
			if (indexed.contains(url)) {
				return null;
			}
			paragraphs = wf.fetchWikipedia(url);
			queueAllLinks(paragraphs, url);
		}
		return url;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
		addToQueue(paragraphs);
	}

	/**
	 * Parses paragraphs and adds all links to the queue.
	 *
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueAllLinks(Elements paragraphs, String url) {
		index.indexPage(url,paragraphs);
		addToQueue(paragraphs);
	}

	private void addToQueue(Elements paragraphs) {
		for (Element paragraph : paragraphs) {
			Elements links = paragraph.getElementsByTag("a");
			for (Element link : links) {
				String url = link.attr("href");
				if (url.startsWith("/wiki")) {
					url = BASE_URL + url;
					queue.add(url);
				}
			}
		}
	}


	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);
		index.deleteAllKeys();
		index.deleteTermCounters();
		index.deleteURLSets();

		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
