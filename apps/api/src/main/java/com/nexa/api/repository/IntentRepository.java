package com.nexa.api.repository;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.Intent;


import java.util.*;

public class IntentRepository {
  public record Example(Intent intent, String text) {}

  public List<Example> examples() {
    return List.of(
        new Example(Intent.GET_BALANCE, "balance"),
        new Example(Intent.GET_BALANCE, "show my balance"),
        new Example(Intent.GET_BALANCE, "what is my available balance"),
        new Example(Intent.GET_BALANCE, "how much money do i have"),
        new Example(Intent.GET_BALANCE, "what is my savings balance"),
        new Example(Intent.GET_BALANCE, "tell me my balance"),
        new Example(Intent.GET_ACCOUNTS, "show my accounts"),
        new Example(Intent.GET_ACCOUNTS, "list bank accounts"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "show transactions"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "show recent transactions"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "what did I spend recently"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "show my last payments"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "show my last few transactions"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "show transactions from last month"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "show debit transactions"),
        new Example(Intent.GET_RECENT_TRANSACTIONS, "show credit transactions"),
        new Example(Intent.GET_TRANSACTION_DETAIL, "transaction details"),
        new Example(Intent.GET_TRANSACTION_DETAIL, "show transaction reference"),
        new Example(Intent.GET_MANDATES, "show mandates"),
        new Example(Intent.GET_MANDATES, "what autopay mandates do I have"),
        new Example(Intent.GET_MANDATES, "show my recurring payments"),
        new Example(Intent.GET_MANDATES, "what subscriptions can debit my account"),
        new Example(Intent.GET_MANDATE_DETAIL, "mandate details"),
        new Example(Intent.GET_MANDATE_DETAIL, "show mandate reference"),
        new Example(Intent.GET_BILLS, "show my bills"),
        new Example(Intent.GET_BILLS, "what bills are due"),
        new Example(Intent.GET_BILLS, "what bills are due this week"),
        new Example(Intent.GET_BILLS, "show upcoming bill payments"),
        new Example(Intent.GET_BILLS, "which bills do I need to pay"),
        new Example(Intent.GET_BILL_DETAIL, "bill details"),
        new Example(Intent.GET_BILL_DETAIL, "show bill reference"),
        new Example(Intent.GET_CREDIT_CARDS, "show my credit card"),
        new Example(Intent.GET_CREDIT_CARDS, "what is my credit card outstanding"),
        new Example(Intent.GET_CARDS, "show my cards"),
        new Example(Intent.GET_CARDS, "card summary"),
        new Example(Intent.GET_CARD_TRANSACTIONS, "show my credit card transactions"),
        new Example(Intent.GET_CARD_TRANSACTIONS, "show card transactions"),
        new Example(Intent.GET_BENEFICIARIES, "show my beneficiaries"),
        new Example(Intent.GET_BENEFICIARIES, "show payees"),
        new Example(Intent.GET_SCHEDULED_PAYMENTS, "what payments are coming up"),
        new Example(Intent.GET_SCHEDULED_PAYMENTS, "show scheduled payments"),
        new Example(Intent.GET_SCHEDULED_PAYMENTS, "show upcoming payments"),
        new Example(Intent.GET_LOANS, "show my loans"),
        new Example(Intent.GET_LOANS, "show my EMIs"),
        new Example(Intent.GET_LOANS, "loan summary"),
        new Example(Intent.GET_LOAN_DETAIL, "loan details"),
        new Example(Intent.GET_LOAN_DETAIL, "show loan reference"),
        new Example(Intent.START_TRANSFER, "send money"),
        new Example(Intent.START_TRANSFER, "I want to transfer money"),
        new Example(Intent.START_TRANSFER, "I want to send money"),
        new Example(Intent.PAY_BILL, "pay my electricity bill"),
        new Example(Intent.START_TRANSFER, "transfer money"),
        new Example(Intent.START_TRANSFER, "send to beneficiary"),
        new Example(Intent.TRANSFER_STATUS, "transfer status"),
        new Example(Intent.TRANSFER_STATUS, "track transfer"),
        new Example(Intent.PAY_BILL, "pay bill"),
        new Example(Intent.PAY_BILL, "prepare bill payment"),
        new Example(Intent.CANCEL_MANDATE, "cancel mandate"),
        new Example(Intent.CANCEL_MANDATE, "cancel autopay"),
        new Example(Intent.PAY_CARD, "pay card"),
        new Example(Intent.PAY_CARD, "prepare card payment"),
        new Example(Intent.HELP, "help"),
        new Example(Intent.HELP, "hello"),
        new Example(Intent.HELP, "hi"),
        new Example(Intent.HELP, "what can you do"));
  }
}
