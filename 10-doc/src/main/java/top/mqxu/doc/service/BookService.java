package top.mqxu.doc.service;

import org.springframework.stereotype.Service;
import top.mqxu.doc.common.BusinessException;
import top.mqxu.doc.common.ErrorCode;
import top.mqxu.doc.common.PageResult;
import top.mqxu.doc.dto.BookCreateRequest;
import top.mqxu.doc.dto.BookQuery;
import top.mqxu.doc.entity.Book;
import top.mqxu.doc.entity.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 图书服务。用内存 Map 充当存储，重点是接口与文档注解，数据层细节不在这里展开。
 *
 * @author mqxu
 */
@Service
public class BookService {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong();

    public BookService() {
        seed("深入理解 Java 虚拟机", "周志明", "9787111641247", Category.TECH, "129.00", 20, "2020-03-01");
        seed("代码整洁之道", "Robert C. Martin", "9787115216878", Category.TECH, "59.00", 35, "2010-01-01");
        seed("人类简史", "尤瓦尔·赫拉利", "9787508647357", Category.HISTORY, "68.00", 12, "2014-11-01");
        seed("三体", "刘慈欣", "9787536692930", Category.LITERATURE, "23.00", 50, "2008-01-01");
        seed("时间简史", "史蒂芬·霍金", "9787535732309", Category.SCIENCE, "45.00", 8, "2010-04-01");
    }

    private void seed(String title, String author, String isbn, Category category,
                      String price, int stock, String publishDate) {
        Book book = new Book();
        book.setId(idGenerator.incrementAndGet());
        book.setTitle(title);
        book.setAuthor(author);
        book.setIsbn(isbn);
        book.setCategory(category);
        book.setPrice(new BigDecimal(price));
        book.setStock(stock);
        book.setPublishDate(LocalDate.parse(publishDate));
        book.setCreateTime(LocalDateTime.now());
        store.put(book.getId(), book);
    }

    public PageResult<Book> page(BookQuery query) {
        int pageNum = Math.max(1, query.getPageNum() == null ? 1 : query.getPageNum());
        int pageSize = Math.min(100, Math.max(1, query.getPageSize() == null ? 10 : query.getPageSize()));

        List<Book> all = store.values().stream()
                .filter(b -> query.getCategory() == null || b.getCategory() == query.getCategory())
                .filter(b -> {
                    String kw = query.getKeyword();
                    if (kw == null || kw.isBlank()) {
                        return true;
                    }
                    return b.getTitle().contains(kw) || b.getAuthor().contains(kw);
                })
                .sorted(Comparator.comparing(Book::getId))
                .toList();

        int from = (pageNum - 1) * pageSize;
        List<Book> list = from >= all.size()
                ? List.of()
                : new ArrayList<>(all.subList(from, Math.min(from + pageSize, all.size())));
        return PageResult.of(list, all.size(), pageNum, pageSize);
    }

    public Book getById(Long id) {
        Book book = store.get(id);
        if (book == null) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }
        return book;
    }

    public Book create(BookCreateRequest request) {
        boolean duplicated = store.values().stream()
                .anyMatch(b -> b.getIsbn().equals(request.getIsbn()));
        if (duplicated) {
            throw new BusinessException(ErrorCode.BOOK_ISBN_DUPLICATED);
        }
        Book book = new Book();
        book.setId(idGenerator.incrementAndGet());
        apply(book, request);
        book.setCreateTime(LocalDateTime.now());
        store.put(book.getId(), book);
        return book;
    }

    public Book update(Long id, BookCreateRequest request) {
        Book book = getById(id);
        boolean duplicated = store.values().stream()
                .anyMatch(b -> !b.getId().equals(id) && b.getIsbn().equals(request.getIsbn()));
        if (duplicated) {
            throw new BusinessException(ErrorCode.BOOK_ISBN_DUPLICATED);
        }
        apply(book, request);
        return book;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }
    }

    public List<Category> categories() {
        return List.of(Category.values());
    }

    private void apply(Book book, BookCreateRequest request) {
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setIsbn(request.getIsbn());
        book.setCategory(request.getCategory());
        book.setPrice(request.getPrice());
        book.setStock(request.getStock() == null ? 0 : request.getStock());
        book.setPublishDate(request.getPublishDate());
    }
}
