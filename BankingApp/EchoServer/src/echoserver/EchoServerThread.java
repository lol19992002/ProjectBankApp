package echoserver;

import database.BankDatabase;

import java.net.*;
import java.io.*;
import java.util.List;

public class EchoServerThread implements Runnable {
    protected Socket socket;
    private String loggedInUser;
    private String loggedInAdmin;
    private String loggedInAccountNumber;

    public EchoServerThread(Socket clientSocket) {
        this.socket = clientSocket;
    }

    public void run() {
        //Deklaracje zmiennych
        BufferedReader brinp;
        DataOutputStream out;
        String threadName = Thread.currentThread().getName();

        //inicjalizacja strumieni
        try {
            brinp = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream()
                    )
            );
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            System.out.println(threadName + "| Błąd przy tworzeniu strumieni " + e);
            return;
        }
        String line;
        boolean loggedIn = false;
        boolean loggedInAdmin = false;
        //pętla główna
        while (true) {
            try {
                line = brinp.readLine();
                // badanie warunku zakończenia pracy
                if ((line == null) || "quit".equals(line)) {
                    out.flush(); // Dodaj wywołanie flush() przed zakończeniem pracy
                    System.out.println(threadName + "| Zakończenie pracy z klientem: " + socket);
                    socket.close();
                    return;
                }
                switch (line) {
                    case "admin":
                        try {
                            boolean adminLoginSuccessful = handleAdminLogin(brinp, out);
                            if (adminLoginSuccessful) {
                                loggedInAdmin = true;
                                out.writeBytes("Login successful\n");
                                out.writeBytes("""
                                        ====Operations====
                                        1. change login
                                        2. change balance
                                        """);
                            } else {
                                out.writeBytes("Login failed\n");
                            }
                            break;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case "register":
                        try {
                            handleRegistration(brinp, out);
                            out.flush();
                            break;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case "login":
                        try {
                            boolean loginSuccessful = handleUserLogin(brinp, out);
                            if (loginSuccessful) {
                                loggedIn = true;
                                loggedInAccountNumber = BankDatabase.getAccountNumberByLogin(loggedInUser);
                                out.writeBytes("Login successful\n");
                                out.writeBytes("""
                                        === Operations ===
                                        1. Deposit
                                        2. Withdraw
                                        3. Get balance(balance)
                                        4. Transfer
                                        5. Info
                                        6. Transaction History
                                        7. Logout
                                        """);

                            } else {
                                out.writeBytes("Login failed\n");
                            }
                            break;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case "logout":
                        loggedIn = false;
                        loggedInUser = null;
                        loggedInAccountNumber = null;
                        out.writeBytes("You have logged out successfully\n");
                        out.flush();
                        break;

                    default:
                        if (loggedInAdmin) {
                        String lowerCaseLine = line.toLowerCase();
                            switch (lowerCaseLine) {
                                case "changelogin":
                                 handleLoginChange(brinp,out);
                                 break;
                                case "balance":
                                    handleBalanceChange(brinp,out);
                                 break;
                            }

                        }
                        if (!loggedIn) {
                            out.writeBytes("You must be logged in!\n");
                            out.flush();
                            break;
                        }  else {
                            String lowerCaseLine = line.toLowerCase();
                            switch (lowerCaseLine) {
                                case "deposit":
                                    handleDeposit(brinp,out);
                                    break;
                                case "withdraw":
                                    handleWithdrawal(brinp,out);
                                    break;
                                case "balance":
                                    handleCheckBalance(out);
                                    break;
                                case "info":
                                    handleGetInfo(out);
                                    break;
                                case "transfer":
                                    handleTransfer(out,brinp);
                                    break;
                                case "transactionhistory":
                                    handleTransactionHistory(out);
                                    break;
                                default:
                                    out.writeBytes("Invalid command\n");
                                    out.flush();
                            }
                        }
                }
            } catch (IOException e) {
                System.out.println(threadName + "| Błąd wejścia-wyjścia." + e);
                return;
            }
        }
    }

    public static void handleUserNameChange(BufferedReader brinp, DataOutputStream out) throws IOException {
        try {
            out.writeBytes("Enter login to find user: \n");
            out.flush();
            String login = brinp.readLine();
            out.writeBytes("Enter new name: \n");
            out.flush();
            String newName = brinp.readLine();
            out.writeBytes("Enter new last name: \n");
            out.flush();
            String newLogin = brinp.readLine();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void handleLoginChange(BufferedReader brinp, DataOutputStream out) throws IOException {
        try {
            out.writeBytes("Enter login to change: \n");
            out.flush();
            String currentLogin = brinp.readLine();
            out.writeBytes("Enter new login: \n");
            out.flush();
            String newLogin = brinp.readLine();
            BankDatabase.ChangeData.changeUserLogin(currentLogin, newLogin);
            out.writeBytes("Login changed successfully!\n");
        } catch (IOException e) {
            out.writeBytes("Failed to change login. Please try again later.\n");
            e.printStackTrace();
        }
        out.flush();
    }

    public static void handleBalanceChange(BufferedReader brinp, DataOutputStream out) throws IOException {
        try {
            out.writeBytes("Enter account number to change balance: \n");
            out.flush();
            String accountNumber = brinp.readLine();
            out.writeBytes("Enter new balance: \n");
            out.flush();
            String newBalance = brinp.readLine();
            BankDatabase.ChangeData.changeUserBalance(accountNumber, Double.parseDouble(newBalance));
            out.writeBytes("Balance changed successfully!\n");
        } catch (IOException e) {
            out.writeBytes("Failed to change balance. Please try again later.\n");
            e.printStackTrace();
        }
        out.flush();
    }

    private void handleRegistration(BufferedReader brinp, DataOutputStream out) throws IOException {
        int maxNameLength = 50;
        int maxAddressLength = 255;

        String name = getInput(brinp, out, "Enter your name:", maxNameLength);
        String lastName = getInput(brinp, out, "Enter your last name:", maxNameLength);
        String login = getInput(brinp, out, "Enter your login:", maxNameLength);
        if (BankDatabase.isLoginExist(login)) {
            out.writeBytes("Login already exists. Please choose a different one.\n");
            out.flush();
            return;
        }
        String password = getInput(brinp, out, "Enter your password:", maxNameLength);
        String address = getInput(brinp, out, "Enter your address:", maxAddressLength);
        int phoneNumber = getPhoneNumber(brinp, out);

        BankDatabase.registerCustomer(name, lastName, login, password, address, phoneNumber);
        String ownerId = String.valueOf(BankDatabase.getCustomerIdByLogin(loggedInUser));
        BankDatabase.createCustomerAccount(ownerId, BankDatabase.getCustomerIdByLogin(login));

        out.writeBytes("User created successfully!\n\r");
        out.flush();
    }
    private String getInput(BufferedReader brinp, DataOutputStream out, String prompt, int maxLength) throws IOException {
        String input;
        do {
            out.writeBytes(prompt + "\n");
            out.flush();
            input = brinp.readLine().trim();
            if (input.isEmpty()) {
                out.writeBytes("Input cannot be empty. Please enter a value.\n");
            } else if (input.length() > maxLength) {
                out.writeBytes("Input is too long. Maximum length is " + maxLength + " characters.\n");
            }
        } while (input.isEmpty() || input.length() > maxLength);
        return input;
    }


    private int getPhoneNumber(BufferedReader brinp, DataOutputStream out) throws IOException {
        String phoneNumberString;
        do {
            phoneNumberString = getInput(brinp, out, "Enter your phone number:", 9);
            if (!phoneNumberString.matches("\\d{9}")) {
                out.writeBytes("Invalid phone number format. Please enter exactly 9 digits.\n");
            }
        } while (!phoneNumberString.matches("\\d{9}"));
        return Integer.parseInt(phoneNumberString);
    }

    private boolean handleUserLogin(BufferedReader brinp, DataOutputStream out) throws IOException {
        out.writeBytes("Enter your login: \n");
        out.flush();
        String login = brinp.readLine();
        out.writeBytes("Enter your password: \n");
        out.flush();
        String password = brinp.readLine();
        out.flush();
        boolean loginSuccess = BankDatabase.checkCredentials(login, password);
        out.flush();
        if (loginSuccess) {
            loggedInUser = login;
            loggedInAccountNumber = BankDatabase.getAccountNumberByLogin(loggedInUser);
        }
        return loginSuccess;
    }

    private boolean handleAdminLogin(BufferedReader brinp, DataOutputStream out) throws IOException {
        out.writeBytes("Enter your login: \n");
        out.flush();
        String login = brinp.readLine();
        out.writeBytes("Enter your password: \n");
        out.flush();
        String password = brinp.readLine();
        out.flush();
        boolean loginSuccess = BankDatabase.checkCredentialsAdmin(login, password);
        out.flush();
        if (loginSuccess) {
            loggedInUser = login;
        }
        return loginSuccess;
    }

    private void handleCheckBalance(DataOutputStream out) throws IOException {
        if (loggedInUser != null) {
            double balance = BankDatabase.getBalanceByLogin(loggedInUser);
            out.writeBytes("Current account balance: " + balance + " zl\n");
            out.flush();
        } else {
            out.writeBytes("You're not logged in!\n");
            out.flush();
        }
    }

    private void handleDeposit(BufferedReader brinp, DataOutputStream out) throws IOException {
        if (loggedInAccountNumber != null) {
            String depositAmountString;
            do {
                out.writeBytes("Enter the amount(zl): \n");
                out.flush();
                depositAmountString = brinp.readLine();
                depositAmountString = depositAmountString.replace(',', '.');
                if (depositAmountString.isEmpty()) {
                    out.writeBytes("Input cannot be empty. Please enter a value.\n");
                } else if (Double.parseDouble(depositAmountString) <= 0) {
                    out.writeBytes("Value must be above 0\n");
                }
            } while (depositAmountString.isEmpty() || (Double.parseDouble(depositAmountString) <= 0));

            BankDatabase.makePayment(loggedInAccountNumber, Double.parseDouble(depositAmountString));
            out.writeBytes("Payment successfully made!\n");
        } else {
            out.writeBytes("You are not logged in or your account number is incorrect!\n");
        }
        out.flush();
    }

    private void handleWithdrawal(BufferedReader brinp, DataOutputStream out) throws IOException {
        if (loggedInAccountNumber != null) {
            String withdrawalAmount;
            do {
                out.writeBytes("Enter the amount(zl): \n");
                out.flush();
                withdrawalAmount = brinp.readLine();
                withdrawalAmount = withdrawalAmount.replace(',', '.');
                if (withdrawalAmount.isEmpty()) {
                    out.writeBytes("Input cannot be empty. Please enter a value.\n");
                } else if (Double.parseDouble(withdrawalAmount) <= 0) {
                    out.writeBytes("Value must be above 0\n");
                }
            } while (withdrawalAmount.isEmpty() || (Double.parseDouble(withdrawalAmount) <= 0));

            BankDatabase.PaymentProcessor.makePaycheck(loggedInAccountNumber, Double.parseDouble(withdrawalAmount));
            out.writeBytes("Withdrawal successfully completed!\n");
        } else {
            out.writeBytes("You are not logged in or your account number is incorrect!\n");
        }
        out.flush();
    }


    private void handleTransfer(DataOutputStream out, BufferedReader brinp) throws IOException {
        if (loggedInAccountNumber != null) {
            String transferAmount;
            do {
                out.writeBytes("Enter the amount(zl): \n");
                out.flush();
                transferAmount = brinp.readLine();
                transferAmount = transferAmount.replace(',', '.');
                if (transferAmount.isEmpty()) {
                    out.writeBytes("Input cannot be empty. Please enter a value.\n");
                } else if (Double.parseDouble(transferAmount) <= 0) {
                    out.writeBytes("Value must be above 0\n");
                }
            } while (transferAmount.isEmpty() || (Double.parseDouble(transferAmount) <= 0));

            String destinationAccountNumber;
            do {
                out.writeBytes("Enter the target account number: \n");
                out.flush();
                destinationAccountNumber = brinp.readLine();
                if (destinationAccountNumber.isEmpty()) {
                    out.writeBytes("Input cannot be empty. Please enter a valid account number.\n");
                } else if (!BankDatabase.isAccountNumberExist(destinationAccountNumber)) {
                    out.writeBytes("Invalid account number. Please enter a valid account number.\n");
                    return;
                }
            } while (!BankDatabase.isAccountNumberExist(destinationAccountNumber) && destinationAccountNumber.isEmpty());

            BankDatabase.makeTransfer(loggedInAccountNumber, destinationAccountNumber, Double.parseDouble(transferAmount));
            out.writeBytes("Transfer successfully completed!\n");
        } else {
            out.writeBytes("You are not logged in or your account number is incorrect!\n");
        }
        out.flush();
    }


    private void handleGetInfo(DataOutputStream out) throws IOException {
        if (loggedInUser != null) {
            String accountInfo = BankDatabase.getAccountInfo(loggedInUser);
            out.writeBytes(accountInfo);
        } else {
            out.writeBytes("You're not logged in!\n");
            out.flush();
        }
        out.flush();
    }

    public void handleTransactionHistory(DataOutputStream out) throws IOException {
        if (loggedInAccountNumber != null) {
            List<String> transactionHistory = BankDatabase.getTransactionHistory(loggedInAccountNumber);
            if (!transactionHistory.isEmpty()) {
                out.writeBytes("Historia transakcji dla konta " + loggedInAccountNumber + ":\n");
                out.flush();
                for (String transaction : transactionHistory) {
                    out.writeBytes(transaction + "\n"); // Dodajemy znak nowej linii po każdej wiadomości
                    out.flush();
                }
            } else {
                out.writeBytes("Brak dokonanych operacji.\n"); // Dodajemy znak nowej linii
                out.flush();
            }
        } else {
            out.writeBytes("Nie jesteś zalogowany!\n"); // Dodajemy znak nowej linii
            out.flush();
        }
    }
}
