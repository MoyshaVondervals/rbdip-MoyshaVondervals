package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomerNameTest {

    @ParameterizedTest
    @CsvSource(value = {
        "Ivan Petrov|Ivan|Petrov",
        "Anna Maria Sidorova|Anna|Maria Sidorova",
        "Prince|Prince|NULL",
        "Иван Иванович Петров|Иван|Иванович Петров",
        "' Alice  Smith '|''|'Alice  Smith '",
        "'Alice '|Alice|''"
    }, delimiter = '|', nullValues = "NULL", ignoreLeadingAndTrailingWhitespace = false)
    void preservesNameAndWhitespace(String fullName, String firstName, String lastName) {
        CustomerName name = CustomerName.fromFullName(fullName);

        assertThat(name.firstName()).isEqualTo(firstName);
        assertThat(name.lastName()).isEqualTo(lastName);
        assertThat(name.fullName()).isEqualTo(fullName);
        assertThat(new Customer(fullName, "Address", null).getFullName()).isEqualTo(fullName);
    }
}
