package com.ekim.bankingapi.transaction;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.exception.InsufficientBalanceException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.nature.NatureService;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final DateTimeFormatter STATEMENT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final float PAGE_MARGIN = 50f;
    private static final float ROW_HEIGHT = 18f;

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final NatureService natureService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional
    public TransactionResponse deposit(Long accountId, BigDecimal amount) {
        validateAmount(amount);

        Account account = accountService.requireOwnedAccount(accountId);

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);

        Transaction transaction = saveTransaction(account, TransactionType.DEPOSIT, amount, newBalance);
        natureService.awardPointsForTransaction(account.getCustomer().getId(), amount);

        auditLogService.log("Transaction", transaction.getId(), "DEPOSIT",
                "Account: " + account.getAccountNumber() + ", Amount: " + amount);

        notificationService.notify(account.getCustomer().getId(), NotificationType.DEPOSIT,
                "Deposit Received", "Your account " + account.getAccountNumber() + " was credited " + amount + ". New balance: " + newBalance);

        log.info("Deposit successful: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
        return TransactionResponse.fromEntity(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(Long accountId, BigDecimal amount) {
        validateAmount(amount);

        Account account = accountService.requireOwnedAccount(accountId);

        if (account.getBalance().compareTo(amount) < 0) {
            log.warn("Withdrawal failed - insufficient balance: accountId={}, requested={}, available={}",
                    accountId, amount, account.getBalance());
            throw new InsufficientBalanceException("Insufficient balance. Current balance: " + account.getBalance());
        }

        accountService.checkAndRecordWithdrawalLimit(account, amount);

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);

        Transaction transaction = saveTransaction(account, TransactionType.WITHDRAWAL, amount, newBalance);
        natureService.awardPointsForTransaction(account.getCustomer().getId(), amount);

        auditLogService.log("Transaction", transaction.getId(), "WITHDRAWAL",
                "Account: " + account.getAccountNumber() + ", Amount: " + amount);

        notificationService.notify(account.getCustomer().getId(), NotificationType.WITHDRAWAL,
                "Withdrawal Completed", "Your account " + account.getAccountNumber() + " was debited " + amount + ". New balance: " + newBalance);

        log.info("Withdrawal successful: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
        return TransactionResponse.fromEntity(transaction);
    }

    public Page<TransactionResponse> getTransactionHistory(Long accountId, Pageable pageable) {
        accountService.requireOwnedAccount(accountId);
        return transactionRepository.findByAccountId(accountId, pageable)
                .map(TransactionResponse::fromEntity);
    }

    public byte[] exportStatementPdf(Long accountId) {
        Account account = accountService.requireOwnedAccount(accountId);
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDesc(accountId);
        return buildPdf(account, transactions);
    }

    private byte[] buildPdf(Account account, List<Transaction> transactions) {
        try (PDDocument document = new PDDocument()) {
            PDFont regularFont = loadFont(document, "fonts/DejaVuSans.ttf");
            PDFont boldFont = loadFont(document, "fonts/DejaVuSans-Bold.ttf");

            PdfCursor cursor = new PdfCursor(document, regularFont, boldFont);
            cursor.startPage();
            cursor.writeStatementHeader(account);
            cursor.writeTableHeader();

            for (Transaction transaction : transactions) {
                cursor.ensureSpaceForRow();
                cursor.writeRow(transaction);
            }

            cursor.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate account statement PDF", e);
        }
    }

    private PDFont loadFont(PDDocument document, String classpathResource) throws IOException {
        try (var inputStream = new ClassPathResource(classpathResource).getInputStream()) {
            return PDType0Font.load(document, inputStream);
        }
    }

    // Encapsulates the running page/content-stream state while the statement PDF is drawn,
    // since PDFBox content streams can't be appended to once a new page starts.
    private static final class PdfCursor {
        private final PDDocument document;
        private final PDFont regularFont;
        private final PDFont boldFont;
        private PDPageContentStream stream;
        private float y;

        PdfCursor(PDDocument document, PDFont regularFont, PDFont boldFont) {
            this.document = document;
            this.regularFont = regularFont;
            this.boldFont = boldFont;
        }

        void startPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - PAGE_MARGIN;
        }

        void writeStatementHeader(Account account) throws IOException {
            writeText(boldFont, 18, PAGE_MARGIN, y, "EverGreen Bank");
            y -= 22;
            writeText(regularFont, 12, PAGE_MARGIN, y, "Hesap Ekstresi");
            y -= 24;
            writeText(regularFont, 10, PAGE_MARGIN, y, "Hesap No: " + account.getAccountNumber());
            y -= 15;
            writeText(regularFont, 10, PAGE_MARGIN, y,
                    "Hesap Sahibi: " + account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName());
            y -= 15;
            writeText(regularFont, 10, PAGE_MARGIN, y,
                    "Oluşturma Tarihi: " + LocalDateTime.now().format(STATEMENT_TIMESTAMP_FORMAT));
            y -= 25;
        }

        void writeTableHeader() throws IOException {
            writeText(boldFont, 10, PAGE_MARGIN, y, "Tarih");
            writeText(boldFont, 10, PAGE_MARGIN + 100, y, "Tür");
            writeText(boldFont, 10, PAGE_MARGIN + 210, y, "Tutar");
            writeText(boldFont, 10, PAGE_MARGIN + 300, y, "Bakiye");
            writeText(boldFont, 10, PAGE_MARGIN + 390, y, "Transfer No");
            y -= 8;
            stream.moveTo(PAGE_MARGIN, y);
            stream.lineTo(PDRectangle.A4.getWidth() - PAGE_MARGIN, y);
            stream.stroke();
            y -= ROW_HEIGHT;
        }

        void ensureSpaceForRow() throws IOException {
            if (y < PAGE_MARGIN + ROW_HEIGHT) {
                stream.close();
                startPage();
                writeTableHeader();
            }
        }

        void writeRow(Transaction transaction) throws IOException {
            writeText(regularFont, 9, PAGE_MARGIN, y, transaction.getTimestamp().format(STATEMENT_TIMESTAMP_FORMAT));
            writeText(regularFont, 9, PAGE_MARGIN + 100, y, transactionTypeLabel(transaction.getType()));
            writeText(regularFont, 9, PAGE_MARGIN + 210, y, transaction.getAmount().toPlainString());
            writeText(regularFont, 9, PAGE_MARGIN + 300, y, transaction.getBalanceAfter().toPlainString());
            writeText(regularFont, 9, PAGE_MARGIN + 390, y,
                    transaction.getTransferId() == null ? "-" : String.valueOf(transaction.getTransferId()));
            y -= ROW_HEIGHT;
        }

        void close() throws IOException {
            stream.close();
        }

        private void writeText(PDFont font, float size, float x, float yPos, String text) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, yPos);
            stream.showText(text);
            stream.endText();
        }

        private String transactionTypeLabel(TransactionType type) {
            return switch (type) {
                case DEPOSIT -> "Para Yatırma";
                case WITHDRAWAL -> "Para Çekme";
                case INTEREST -> "Faiz Ödemesi";
            };
        }
    }

    private Transaction saveTransaction(Account account, TransactionType type, BigDecimal amount, BigDecimal balanceAfter) {
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        return transactionRepository.save(transaction);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Amount must be greater than zero");
        }
    }
}