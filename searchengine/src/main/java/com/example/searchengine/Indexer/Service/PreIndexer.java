package com.example.searchengine.Indexer.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import opennlp.tools.stemmer.PorterStemmer;

@Service
public class PreIndexer {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    public List<String> getStopWords() {
        List<String> stopWords = new ArrayList<>();
        try (InputStream inputStream = PreIndexer.class.getClassLoader().getResourceAsStream("stopWords.txt")) {
            if (inputStream == null) {
                throw new RuntimeException("Resource file stopWords.txt not found");
            }
            Scanner scanner = new Scanner(inputStream);
            while (scanner.hasNextLine()) {
                String word = scanner.nextLine();
                stopWords.add(word);
            }
            scanner.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
        return stopWords;
    }

    public List<String> removeStopWords(List<String> docs) {
        List<String> stopWords = getStopWords();
        docs.removeAll(stopWords);
        docs.removeIf(item -> item == null || item.isEmpty() || item.length() <= 1); // remove empty places and single characters
        return docs;
    }

    public String cleanHTML(String paragraph) {
        // Parse the HTML string into a Document object
        Document doc = Jsoup.parse(paragraph);

        // Extract code blocks first to preserve them - they're especially important for programming content
        StringBuilder codeContent = new StringBuilder();
        doc.select("code, pre, tt, kbd, samp").forEach(element -> {
            codeContent.append(" ").append(element.text()).append(" ");
        });
        
        // Remove unwanted tags but keep more semantic ones compared to before
        doc.select("style, script, meta, link, noscript, svg, canvas").remove();

        // Extract the cleaned text (strips all remaining HTML tags)
        String cleanedText = doc.text();
        
        // Add back the code content to ensure it's part of the indexed text
        cleanedText += " " + codeContent.toString();

        // Preserve a wide range of programming-relevant characters
        // Keep alphanumeric characters and many special characters relevant to programming
        cleanedText = cleanedText.replaceAll("[^a-zA-Z0-9_\\+\\-\\.\\#\\$\\%\\^\\&\\*\\(\\)\\[\\]\\{\\}\\<\\>\\=\\/\\\\\\|\\:\\;\\,\\!\\?]", " ");
        
        // Replace multiple spaces with a single space
        cleanedText = cleanedText.replaceAll("\\s+", " ").trim();

        return cleanedText;
    }

    // testing till the crawler is ready and reindexing pages in case of any updates
    public String fetchAndCleanHTML(String url) {
        try {
            Document pageDocument = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "*")
                    .timeout(10000) // Increased timeout for better reliability
                    .execute()
                    .parse();

            return cleanHTML(pageDocument.html());
        } catch (Exception e) {
            throw new RuntimeException("Error fetching or cleaning page: " + e.getMessage(), e);
        }
    }

    public List<String> tokenize(String text) {
        text = text.toLowerCase();
        List<String> words = new Vector<>();
        
        // Pattern to match programming tokens, greatly expanded from before
        // This pattern captures:
        // - Regular words (e.g., "hello")
        // - Programming identifiers with underscores (e.g., "variable_name")
        // - Dotted expressions (e.g., "object.method")
        // - Numbers (e.g., "42", "3.14")
        // - Expressions with symbols (e.g., "x+=1", "arr[i]")
        // - File extensions (e.g., ".py", ".js")
        // - URLs and paths
        Pattern pattern = Pattern.compile(
            "(?:[a-z0-9_]+(?:\\.[a-z0-9_]+)*)" + // words with optional dots
            "|(?:[a-z0-9_]+(?:[\\+\\-\\*\\/\\=](?:[a-z0-9_]+))?)" + // expressions like "x+y"
            "|(?:[a-z0-9_]+(?:\\[(?:[a-z0-9_\\*]+)\\])?)" + // array access like "arr[i]"
            "|(?:\\.[a-z0-9_]+)" + // file extensions like ".py"
            "|(?:/[a-z0-9_\\-\\./]+)" // file paths or URL parts
        );
        
        Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            String token = matcher.group();
            if (token == null || token.isEmpty()) {
                continue;
            }
            
            // Add the whole token
            words.add(token);
            
            // Split compound tokens to increase indexing coverage
            
            // Handle dotted tokens (like "module.function")
            if (token.contains(".")) {
                String[] parts = token.split("\\.");
                for (String part : parts) {
                    if (part != null && !part.isEmpty() && !words.contains(part)) {
                        words.add(part);
                    }
                }
            }
            
            // Handle tokens with operators (like "x+y")
            if (token.matches(".*[\\+\\-\\*\\/\\=].*")) {
                String[] parts = token.split("[\\+\\-\\*\\/\\=]");
                for (String part : parts) {
                    if (part != null && !part.isEmpty() && !words.contains(part)) {
                        words.add(part);
                    }
                }
            }
            
            // Handle tokens with brackets (like "array[index]")
            if (token.contains("[") && token.contains("]")) {
                String base = token.substring(0, token.indexOf('['));
                String index = token.substring(token.indexOf('[') + 1, token.indexOf(']'));
                
                if (base != null && !base.isEmpty() && !words.contains(base)) {
                    words.add(base);
                }
                
                if (index != null && !index.isEmpty() && !words.contains(index)) {
                    words.add(index);
                }
            }
        }
        
        return words;
    }

    public List<String> Stemming(List<String> words) {
        List<String> stemmedWords = new Vector<>();
        PorterStemmer ps = new PorterStemmer();
        for (String word : words) {
            try {
                String stemmed = ps.stem(word);
                stemmedWords.add(stemmed);
            } catch (Exception e) {
                // If stemming fails (e.g., for non-text tokens), keep the original
                stemmedWords.add(word);
            }
        }
        return stemmedWords;
    }
}
