# Nexa complete tracked-file inventory — 25 September 2026

Baseline: rb-fe @ 2d5ce83. **443 files** before these audit notes. Every baseline tracked path is listed once. Role classification is based on directory, framework loading and the audit's reference checks; it does not claim each method or CSS selector executes in every session. Git internals, dependencies and generated output are excluded. See [usage evidence and cleanup cautions](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/UNUSED_AND_LEGACY.md).

## apps/api (217)

| File | Role/status |
|---|---|
| [apps/api/.gitignore](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/.gitignore) | Repository hygiene configuration |
| [apps/api/.mvn/wrapper/maven-wrapper.properties](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/.mvn/wrapper/maven-wrapper.properties) | Maven bootstrap |
| [apps/api/README.md](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/README.md) | Documentation — check date/currentness; not runtime |
| [apps/api/docs/authentication.md](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/docs/authentication.md) | Documentation — check date/currentness; not runtime |
| [apps/api/docs/local-oracle-setup.md](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/docs/local-oracle-setup.md) | Documentation — check date/currentness; not runtime |
| [apps/api/docs/microservice-architecture.md](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/docs/microservice-architecture.md) | Documentation — check date/currentness; not runtime |
| [apps/api/mvnw](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/mvnw) | Maven bootstrap |
| [apps/api/mvnw.cmd](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/mvnw.cmd) | Maven bootstrap |
| [apps/api/pom.xml](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/pom.xml) | Dependency/build manifest or reproducibility lockfile |
| [apps/api/src/main/java/com/nexa/api/NexaApiApplication.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/NexaApiApplication.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/Account.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/Account.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/AccountBalanceResponse.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/AccountBalanceResponse.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/AccountCategory.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/AccountCategory.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/AccountResponse.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/AccountResponse.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/AccountStatus.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/AccountStatus.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/AccountType.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/AccountType.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/AuthenticationResponse.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/AuthenticationResponse.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/BankTransaction.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/BankTransaction.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/BankingContent.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/BankingContent.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/BankingLanguage.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/BankingLanguage.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/BankingModels.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/BankingModels.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/Customer.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/Customer.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/CustomerProfileResponse.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/CustomerProfileResponse.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/FastBankingIntent.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/FastBankingIntent.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/Intent.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/Intent.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/JournalEntry.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/JournalEntry.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/JournalEntryStatus.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/JournalEntryStatus.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/JournalEntryType.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/JournalEntryType.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/LedgerEntry.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/LedgerEntry.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/LedgerEntryType.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/LedgerEntryType.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/LoanModels.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/LoanModels.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/LoanSalarySlip.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/LoanSalarySlip.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/LoginRequest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/LoginRequest.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/OpenAccountRequest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/OpenAccountRequest.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/PageResponse.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/PageResponse.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/RefreshTokenEntity.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/RefreshTokenEntity.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/RefreshTokenRequest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/RefreshTokenRequest.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/RegisterRequest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/RegisterRequest.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/TransactionInstruction.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionInstruction.java) | JPA-discovered entity — schema/test role; not proven dead |
| [apps/api/src/main/java/com/nexa/api/beans/TransactionQuery.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionQuery.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/TransactionQueryResult.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionQueryResult.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/TransactionRequest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionRequest.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/TransactionResponse.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionResponse.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/TransactionStatus.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionStatus.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/TransactionType.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionType.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/UpdateProfileRequest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/UpdateProfileRequest.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/beans/UserEntity.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/UserEntity.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/config/BankingTimeConfiguration.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/config/BankingTimeConfiguration.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/config/CorrelationIdFilter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/config/CorrelationIdFilter.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/config/CorsConfiguration.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/config/CorsConfiguration.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/config/LoanUploadConfiguration.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/config/LoanUploadConfiguration.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/config/NlpConfiguration.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/config/NlpConfiguration.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/config/TomcatConfiguration.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/config/TomcatConfiguration.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/AccountController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/AccountController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/AccountsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/AccountsController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/ActionsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/ActionsController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/AdminAccountsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/AdminAccountsController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/AdminLoansController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/AdminLoansController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/AuthenticationController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/AuthenticationController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/BeneficiariesController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/BeneficiariesController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/BillController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/BillController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/CardController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/CardController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/ConversationsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/ConversationsController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/CreditCardsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/CreditCardsController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/CustomerController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/CustomerController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/HealthController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/HealthController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/JournalEntryController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/JournalEntryController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/LedgerEntryController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/LedgerEntryController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/LoanController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/LoanController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/LoanDocumentsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/LoanDocumentsController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/MandateController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/MandateController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/MeController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/MeController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/MoneyTransferController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/MoneyTransferController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/ScheduledPaymentController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/ScheduledPaymentController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/ShowcaseController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/ShowcaseController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/TransactionController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/TransactionController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/controller/TransactionsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/TransactionsController.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/exep/ApiError.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/exep/ApiError.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/exep/ApiExceptionHandler.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/exep/ApiExceptionHandler.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/exep/ConflictException.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/exep/ConflictException.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/exep/InvalidRequestException.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/exep/InvalidRequestException.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/exep/ResourceNotFoundException.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/exep/ResourceNotFoundException.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/exep/UnauthorizedException.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/exep/UnauthorizedException.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/AccountDao.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/AccountDao.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/BankingProductRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/BankingProductRepository.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/CustomerDao.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/CustomerDao.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/IntentRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/IntentRepository.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/JdbcBankingProductRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/JdbcBankingProductRepository.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/JournalEntryDao.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/JournalEntryDao.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/LedgerEntryDao.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/LedgerEntryDao.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/RefreshTokenRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/RefreshTokenRepository.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/TransactionDao.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/TransactionDao.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/repository/UserRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/UserRepository.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/security/JwtService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/security/JwtService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/security/SecurityConfiguration.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/security/SecurityConfiguration.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/security/SecurityErrorWriter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/security/SecurityErrorWriter.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AccountOpeningService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AccountOpeningService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AccountQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AccountQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AccountService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AccountService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AccountServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AccountServiceImpl.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/ActionPreparationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ActionPreparationService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AdminAccountService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AdminAccountService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AdminBootstrap.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AdminBootstrap.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AdminLoanService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AdminLoanService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AuthenticatedCurrentUserProvider.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AuthenticatedCurrentUserProvider.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/AuthenticationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AuthenticationService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/BankingDomainRouter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/BankingDomainRouter.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/BasicEmbeddingProvider.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/BasicEmbeddingProvider.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/BasicEntityExtractor.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/BasicEntityExtractor.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/BasicIntentClassifier.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/BasicIntentClassifier.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/BeneficiaryQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/BeneficiaryQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/BillQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/BillQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/BusinessDateResolver.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/BusinessDateResolver.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/CardQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CardQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/ConversationInsights.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ConversationInsights.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/ConversationInterpreter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ConversationInterpreter.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/ConversationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ConversationService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/CreditMandateService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CreditMandateService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/CurrentUserProvider.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CurrentUserProvider.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/CustomerQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CustomerQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/CustomerService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CustomerService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/CustomerServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CustomerServiceImpl.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/CustomerTransferService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CustomerTransferService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/DomainRouter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/DomainRouter.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/EmbeddingProvider.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/EmbeddingProvider.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/EntityExtractor.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/EntityExtractor.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/InMemoryVectorIndex.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/InMemoryVectorIndex.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/IntentClassifier.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/IntentClassifier.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/JournalEntryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/JournalEntryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/JournalEntryServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/JournalEntryServiceImpl.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/KnowledgeBase.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/KnowledgeBase.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/KnowledgeRouter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/KnowledgeRouter.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/LedgerEntryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LedgerEntryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/LedgerEntryServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LedgerEntryServiceImpl.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/LoanApplicationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LoanApplicationService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/LoanCalculationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LoanCalculationService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/LoanDocumentService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LoanDocumentService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/LoanQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LoanQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/LoanSettlementService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LoanSettlementService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/MandateQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/MandateQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/MoneyTransferService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/MoneyTransferService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/NaturalLanguageTransactionQueryParser.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/NaturalLanguageTransactionQueryParser.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/OllamaInterpreter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/OllamaInterpreter.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/PaymentItemService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/PaymentItemService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/ProductQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ProductQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/ScheduledPaymentQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ScheduledPaymentQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/ShowcaseService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ShowcaseService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/SpendingQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/SpendingQueryService.java) | Spring bean with uncalled aggregation method — investigate/reuse |
| [apps/api/src/main/java/com/nexa/api/service/TransactionQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/TransactionService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/TransactionServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionServiceImpl.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/TransferQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransferQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/UserQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/UserQueryService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/VectorIndex.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/VectorIndex.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/Workflow.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/Workflow.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/com/nexa/api/service/WorkflowService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/WorkflowService.java) | API runtime or framework-discovered Java source |
| [apps/api/src/main/java/db/migration/V17__migrate_banking_products.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/db/migration/V17__migrate_banking_products.java) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/application-example.properties](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/application-example.properties) | API runtime configuration/classpath resource |
| [apps/api/src/main/resources/application-local.properties](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/application-local.properties) | API runtime configuration/classpath resource |
| [apps/api/src/main/resources/application.properties](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/application.properties) | API runtime configuration/classpath resource |
| [apps/api/src/main/resources/db/local-migration/V10__seed_banking_read_models.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/local-migration/V10__seed_banking_read_models.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/local-migration/V2__seed_local_demo_data.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/local-migration/V2__seed_local_demo_data.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/local-migration/V4__enable_local_demo_login.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/local-migration/V4__enable_local_demo_login.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V11__integrate_authoritative_banking_core.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V11__integrate_authoritative_banking_core.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V12__conversation_workflows.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V12__conversation_workflows.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V13__conversation_action_audit.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V13__conversation_action_audit.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V14__money_transfer_requests.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V14__money_transfer_requests.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V15__showcase_actions.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V15__showcase_actions.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V16__six_table_structure.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V16__six_table_structure.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V18__six_table_constraints.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V18__six_table_constraints.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V19__admin_audit_and_transfer_cancellation.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V19__admin_audit_and_transfer_cancellation.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V1__create_core_banking_schema.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V1__create_core_banking_schema.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V20__loan_amortization.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V20__loan_amortization.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V21__admin_loan_approval_and_bank_funding.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V21__admin_loan_approval_and_bank_funding.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V22__linked_payee_destination_hash.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V22__linked_payee_destination_hash.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V23__loan_salary_slips.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V23__loan_salary_slips.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V3__add_authentication_schema.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V3__add_authentication_schema.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V5__merge_customer_profiles_and_prepare_account_opening.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V5__merge_customer_profiles_and_prepare_account_opening.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V6__add_payments_and_platform_entities.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V6__add_payments_and_platform_entities.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V7__create_conversation_history.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V7__create_conversation_history.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V8__add_structured_conversation_content.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V8__add_structured_conversation_content.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/db/migration/V9__add_banking_read_models.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V9__add_banking_read_models.sql) | Migration or seed — preserve version/history |
| [apps/api/src/main/resources/knowledge/nexa.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/knowledge/nexa.json) | API runtime configuration/classpath resource |
| [apps/api/src/test/java/com/nexa/api/accounts/AccountOpeningServiceTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/accounts/AccountOpeningServiceTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/accounts/AccountsControllerTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/accounts/AccountsControllerTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/ActionPreparationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/ActionPreparationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/BankingDomainTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/BankingDomainTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/BankingEndpointsTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/BankingEndpointsTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/LoanCalculationServiceTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/LoanCalculationServiceTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/LoanIntegrationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/LoanIntegrationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/LoanTestSupport.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/LoanTestSupport.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/MigrationVersionTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/MigrationVersionTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/MoneyTransferIntegrationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/MoneyTransferIntegrationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/banking/SixTableBankingIntegrationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/SixTableBankingIntegrationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/conversations/BankingContentTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/conversations/BankingContentTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/conversations/ChatFirstIntegrationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/conversations/ChatFirstIntegrationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/conversations/ConversationInterpreterTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/conversations/ConversationInterpreterTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/conversations/ConversationServiceTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/conversations/ConversationServiceTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/conversations/ConversationsControllerTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/conversations/ConversationsControllerTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/conversations/DomainRoutingTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/conversations/DomainRoutingTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/conversations/InterpreterFixture.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/conversations/InterpreterFixture.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/core/BankingCoreIntegrationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/core/BankingCoreIntegrationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/core/MergedApplicationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/core/MergedApplicationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/customer/MeControllerTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/customer/MeControllerTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/health/HealthControllerTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/health/HealthControllerTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/identity/AuthenticationControllerTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/identity/AuthenticationControllerTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/identity/AuthenticationServiceTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/identity/AuthenticationServiceTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/nlp/BankingLanguageTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/nlp/BankingLanguageTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/nlp/BasicIntentClassifierTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/nlp/BasicIntentClassifierTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/nlp/FastBankingIntentTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/nlp/FastBankingIntentTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/nlp/OllamaInterpreterTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/nlp/OllamaInterpreterTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/security/SecurityConfigurationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/security/SecurityConfigurationTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/service/KnowledgeRouterTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/service/KnowledgeRouterTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/transactions/BusinessDateResolverTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/transactions/BusinessDateResolverTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/java/com/nexa/api/transactions/TransactionQueryServiceTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/transactions/TransactionQueryServiceTest.java) | Test/fixture/manual QA — development use |
| [apps/api/src/test/resources/application-test.properties](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/resources/application-test.properties) | Test/fixture/manual QA — development use |
| [apps/api/src/test/resources/six-table-chat.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/resources/six-table-chat.sql) | Test/fixture/manual QA — development use |

## apps/web (129)

| File | Role/status |
|---|---|
| [apps/web/oraclejetconfig.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/oraclejetconfig.json) | Build/bootstrap/deployment configuration or tooling |
| [apps/web/package-lock.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/package-lock.json) | Dependency/build manifest or reproducibility lockfile |
| [apps/web/package.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/package.json) | Dependency/build manifest or reproducibility lockfile |
| [apps/web/path_mapping.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/path_mapping.json) | Build/bootstrap/deployment configuration or tooling |
| [apps/web/scripts/hooks/after_app_create.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_app_create.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_app_restore.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_app_restore.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_app_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_app_typescript.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_build.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_component_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_component_build.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_component_create.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_component_create.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_component_package.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_component_package.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_component_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_component_typescript.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_serve.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_serve.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/after_watch.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/after_watch.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_app_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_app_typescript.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_build.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_component_optimize.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_component_optimize.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_component_package.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_component_package.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_component_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_component_typescript.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_injection.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_injection.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_optimize.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_optimize.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_release_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_release_build.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_serve.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_serve.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_watch.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_watch.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/before_webpack.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/before_webpack.js) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/hooks/hooks.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/hooks/hooks.json) | JET lifecycle tooling/scaffolding — inspect registration |
| [apps/web/scripts/lint.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/scripts/lint.cjs) | Build/bootstrap/deployment configuration or tooling |
| [apps/web/src/components/BankingIcon.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/BankingIcon.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/ConnectionNotice.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/ConnectionNotice.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/LanguageSelect.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/LanguageSelect.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/SidebarNavigation.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/SidebarNavigation.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/app.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/app.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/auth/AuthPage.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/auth/AuthPage.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/AccountSettings.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/AccountSettings.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/AssistantResponse.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/AssistantResponse.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/BankingCollection.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/BankingCollection.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/BankingResponse.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/BankingResponse.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/ConversationHistoryItem.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/ConversationHistoryItem.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/ConversationTools.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/ConversationTools.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/ConversationWorkspace.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/ConversationWorkspace.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/MessageBubble.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/MessageBubble.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/SpendingSummary.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/SpendingSummary.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/chat/WorkflowCard.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/WorkflowCard.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/design/Action.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/design/Action.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/design/WorkspaceRail.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/design/WorkspaceRail.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/landing/LandingPage.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/landing/LandingPage.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/components/landing/goals-sequence.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/landing/goals-sequence.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/Accounts.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Accounts.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/Activity.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Activity.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/AdminApp.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/AdminApp.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/AdminLoanQueue.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/AdminLoanQueue.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/BankingApp.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/BankingApp.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/LoanPrepayment.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/LoanPrepayment.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/LoanSalarySlips.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/LoanSalarySlips.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/LoanTools.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/LoanTools.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/MoneyTransfer.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/MoneyTransfer.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/PayeeForm.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/PayeeForm.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/Payments.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Payments.tsx) | Active module with unreachable OperationsPage branch |
| [apps/web/src/features/banking/ProductOperations.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/ProductOperations.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/Products.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Products.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/Showcase.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Showcase.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/api.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/api.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/demo-api.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/demo-api.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/money-transfers.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/money-transfers.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/ui.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/ui.tsx) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/features/banking/utils.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/utils.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/hooks/useNavigationGuard.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/hooks/useNavigationGuard.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/hooks/useVoiceInput.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/hooks/useVoiceInput.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/index.html](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/index.html) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/index.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/index.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/main.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/main.js) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/services/activity-summary.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/activity-summary.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/services/auth.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/auth.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/services/banking-content.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/banking-content.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/services/banking.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/banking.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/services/conversations.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/conversations.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/services/locale.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/locale.ts) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/admin.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/admin.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/app.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/app.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/auth-page.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/auth-page.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/auth.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/auth.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/banking-forms.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/banking-forms.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/banking.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/banking.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/brand-transition.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/brand-transition.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/controls.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/controls.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/conversation-first.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/conversation-first.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/conversation-results.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/conversation-results.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/experience.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/experience.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/goals-sequence.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/goals-sequence.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/images/nexa-conversation-portal.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/images/nexa-conversation-portal.png) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/images/nexa-cta-portrait-desktop.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/images/nexa-cta-portrait-desktop.png) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/images/nexa-cta-portrait.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/images/nexa-cta-portrait.png) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/images/nexa-voice-portrait.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/images/nexa-voice-portrait.png) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/images/nexa.svg](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/images/nexa.svg) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/landing.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/landing.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/profile.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/profile.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/sidebar.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/sidebar.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/src/styles/tokens.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/styles/tokens.css) | Integrated frontend source/resource — reference graph reachable |
| [apps/web/tests/MESSENGER_QA.md](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/MESSENGER_QA.md) | Test/fixture/manual QA — development use |
| [apps/web/tests/activity-summary.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/activity-summary.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/admin-accounts.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/admin-accounts.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/admin-dialog-layout.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/admin-dialog-layout.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/admin-loans.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/admin-loans.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/admin-ui-fixtures.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/admin-ui-fixtures.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/assistant-response.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/assistant-response.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/auth-experience.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/auth-experience.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/banking-app.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/banking-app.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/banking-content.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/banking-content.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/banking-form-layout.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/banking-form-layout.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/chat-first.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/chat-first.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/conversation-delete.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/conversation-delete.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/conversation-redesign.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/conversation-redesign.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/jet-node-stubs.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/jet-node-stubs.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/landing-session.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/landing-session.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/live-banking.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/live-banking.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/loan-calculators.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/loan-calculators.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/loan-salary-slips.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/loan-salary-slips.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/messenger-fixture.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/messenger-fixture.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/money-transfer.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/money-transfer.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/navigation-loading.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/navigation-loading.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/palette.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/palette.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/product-operations.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/product-operations.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/showcase.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/showcase.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/sidebar.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/sidebar.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/source-loader.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/source-loader.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/startup-experience.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/startup-experience.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tests/voice-input.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tests/voice-input.test.cjs) | Test/fixture/manual QA — development use |
| [apps/web/tsconfig.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/tsconfig.json) | Build/bootstrap/deployment configuration or tooling |
| [apps/web/vercel.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/vercel.json) | Build/bootstrap/deployment configuration or tooling |

## apps/frontend (60)

| File | Role/status |
|---|---|
| [apps/frontend/.gitignore](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/.gitignore) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/README.md](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/README.md) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/oraclejetconfig.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/oraclejetconfig.json) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/package-lock.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/package-lock.json) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/package.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/package.json) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/path_mapping.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/path_mapping.json) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_app_create.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_app_create.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_app_restore.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_app_restore.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_app_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_app_typescript.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_build.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_component_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_component_build.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_component_create.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_component_create.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_component_package.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_component_package.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_component_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_component_typescript.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_serve.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_serve.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/after_watch.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/after_watch.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_app_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_app_typescript.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_build.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_component_optimize.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_component_optimize.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_component_package.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_component_package.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_component_typescript.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_component_typescript.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_injection.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_injection.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_optimize.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_optimize.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_release_build.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_release_build.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_serve.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_serve.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_watch.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_watch.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/before_webpack.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/before_webpack.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/scripts/hooks/hooks.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/scripts/hooks/hooks.json) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/components/app.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/app.tsx) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/components/auth-page.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/auth-page.tsx) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/components/banking-demo.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/banking-demo.ts) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/components/brand-transition.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/brand-transition.tsx) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/components/chat-workspace.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/chat-workspace.tsx) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/components/content/index.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/content/index.tsx) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/src/components/footer.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/footer.tsx) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/src/components/goals-sequence.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/goals-sequence.tsx) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/components/header.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/header.tsx) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/src/components/voice-session.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/components/voice-session.ts) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/index.html](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/index.html) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/index.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/index.ts) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/main.js](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/main.js) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/app.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/app.css) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/auth-page.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/auth-page.css) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/brand-transition.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/brand-transition.css) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/chat-workspace.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/chat-workspace.css) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/fonts/App_iconfont.woff](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/fonts/App_iconfont.woff) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/src/styles/goals-sequence.css](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/goals-sequence.css) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/images/JET-Favicon-Red-32x32.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/JET-Favicon-Red-32x32.png) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/src/styles/images/avatar_24px.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/avatar_24px.png) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/src/styles/images/avatar_24px_2x.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/avatar_24px_2x.png) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/src/styles/images/nexa-conversation-portal.md](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/nexa-conversation-portal.md) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/images/nexa-conversation-portal.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/nexa-conversation-portal.png) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/images/nexa-cta-portrait-desktop.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/nexa-cta-portrait-desktop.png) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/images/nexa-cta-portrait.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/nexa-cta-portrait.png) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/images/nexa-voice-portrait.png](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/nexa-voice-portrait.png) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/images/nexa.svg](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/nexa.svg) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/src/styles/images/oracle_logo.svg](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/src/styles/images/oracle_logo.svg) | Reference prototype — orphan starter file; cleanup candidate |
| [apps/frontend/tests/banking-demo.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/tests/banking-demo.test.cjs) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/tests/voice-session.test.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/tests/voice-session.test.cjs) | Retained standalone prototype/reference; excluded from integrated runtime |
| [apps/frontend/tsconfig.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/frontend/tsconfig.json) | Retained standalone prototype/reference; excluded from integrated runtime |

## docs (24)

| File | Role/status |
|---|---|
| [docs/BACKEND_HANDOFF.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/BACKEND_HANDOFF.md) | Documentation — check date/currentness; not runtime |
| [docs/BANKING_INTEGRATION.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/BANKING_INTEGRATION.md) | Documentation — check date/currentness; not runtime |
| [docs/CHAT_FIRST_BANKING.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/CHAT_FIRST_BANKING.md) | Documentation — check date/currentness; not runtime |
| [docs/CONVERSATIONAL_BANKING_BACKEND.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/CONVERSATIONAL_BANKING_BACKEND.md) | Documentation — check date/currentness; not runtime |
| [docs/CONVERSATION_CONTEXT.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/CONVERSATION_CONTEXT.md) | Documentation — check date/currentness; not runtime |
| [docs/CONVERSATION_REDESIGN.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/CONVERSATION_REDESIGN.md) | Documentation — check date/currentness; not runtime |
| [docs/CONVERSATION_STORAGE.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/CONVERSATION_STORAGE.md) | Documentation — check date/currentness; not runtime |
| [docs/DEMO_READY.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/DEMO_READY.md) | Documentation — check date/currentness; not runtime |
| [docs/FRONTEND_AUDIT.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/FRONTEND_AUDIT.md) | Documentation — check date/currentness; not runtime |
| [docs/FRONTEND_CLEANUP.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/FRONTEND_CLEANUP.md) | Documentation — check date/currentness; not runtime |
| [docs/FRONTEND_EXPERIENCE.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/FRONTEND_EXPERIENCE.md) | Documentation — check date/currentness; not runtime |
| [docs/FRONTEND_IMPLEMENTATION.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/FRONTEND_IMPLEMENTATION.md) | Documentation — check date/currentness; not runtime |
| [docs/FRONTEND_PALETTE.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/FRONTEND_PALETTE.md) | Documentation — check date/currentness; not runtime |
| [docs/KNOWLEDGE_BASE.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/KNOWLEDGE_BASE.md) | Documentation — check date/currentness; not runtime |
| [docs/LOANS.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/LOANS.md) | Documentation — check date/currentness; not runtime |
| [docs/LOCAL_CHAT_MODEL.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/LOCAL_CHAT_MODEL.md) | Documentation — check date/currentness; not runtime |
| [docs/MODERN_HERITAGE_USABILITY.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/MODERN_HERITAGE_USABILITY.md) | Documentation — check date/currentness; not runtime |
| [docs/MONEY_TRANSFERS.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/MONEY_TRANSFERS.md) | Documentation — check date/currentness; not runtime |
| [docs/SHOWCASE.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/SHOWCASE.md) | Documentation — check date/currentness; not runtime |
| [docs/SIDEBAR_NAVIGATION.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/SIDEBAR_NAVIGATION.md) | Documentation — check date/currentness; not runtime |
| [docs/SIX_TABLE_BANKING.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/SIX_TABLE_BANKING.md) | Documentation — check date/currentness; not runtime |
| [docs/SIX_TABLE_SCHEMA.sql](C:/Users/Purva/Project/BANK_APP/Nexa/docs/SIX_TABLE_SCHEMA.sql) | Documentation — check date/currentness; not runtime |
| [docs/STRUCTURED_BANKING_RESPONSES.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/STRUCTURED_BANKING_RESPONSES.md) | Documentation — check date/currentness; not runtime |
| [docs/TRANSFERS_AND_ADMIN.md](C:/Users/Purva/Project/BANK_APP/Nexa/docs/TRANSFERS_AND_ADMIN.md) | Documentation — check date/currentness; not runtime |

## scripts (6)

| File | Role/status |
|---|---|
| [scripts/java/InspectDatabase.java](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/java/InspectDatabase.java) | Database setup/inspection/cutover utility — operator use |
| [scripts/java/SetupDatabase.java](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/java/SetupDatabase.java) | Database setup/inspection/cutover utility — operator use |
| [scripts/java/SixTableCutover.java](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/java/SixTableCutover.java) | Database setup/inspection/cutover utility — operator use |
| [scripts/setup-db.ps1](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/setup-db.ps1) | Database setup/inspection/cutover utility — operator use |
| [scripts/six-table-cutover.ps1](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/six-table-cutover.ps1) | Database setup/inspection/cutover utility — operator use |
| [scripts/tests/SetupDatabaseTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/tests/SetupDatabaseTest.java) | Test/fixture/manual QA — development use |

## tools (3)

| File | Role/status |
|---|---|
| [tools/demo/DemoDataMaintenance.java](C:/Users/Purva/Project/BANK_APP/Nexa/tools/demo/DemoDataMaintenance.java) | Optional demo operation — current contract issues noted |
| [tools/demo/README.md](C:/Users/Purva/Project/BANK_APP/Nexa/tools/demo/README.md) | Optional demo operation — current contract issues noted |
| [tools/demo/seed-demo.cjs](C:/Users/Purva/Project/BANK_APP/Nexa/tools/demo/seed-demo.cjs) | Optional demo operation — current contract issues noted |

## root/CI (4)

| File | Role/status |
|---|---|
| [.github/workflows/backend-ci.yml](C:/Users/Purva/Project/BANK_APP/Nexa/.github/workflows/backend-ci.yml) | CI configuration — rb-fe/path-scoped |
| [.github/workflows/frontend-ci.yml](C:/Users/Purva/Project/BANK_APP/Nexa/.github/workflows/frontend-ci.yml) | CI configuration — rb-fe/path-scoped |
| [.gitignore](C:/Users/Purva/Project/BANK_APP/Nexa/.gitignore) | Repository hygiene configuration |
| [README.md](C:/Users/Purva/Project/BANK_APP/Nexa/README.md) | Documentation — check date/currentness; not runtime |


