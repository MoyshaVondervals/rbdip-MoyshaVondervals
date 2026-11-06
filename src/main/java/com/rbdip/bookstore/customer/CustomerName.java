package com.rbdip.bookstore.customer;


record CustomerName(String firstName, String lastName) {
    static CustomerName fromFullName(String fullName) {
        int separator = fullName.indexOf(' ');
        return separator < 0 ? new CustomerName(fullName, null)
                : new CustomerName(fullName.substring(0, separator), fullName.substring(separator + 1));
    }
    String fullName() {
        return lastName == null ? firstName : firstName + " " + lastName;
    }
}
