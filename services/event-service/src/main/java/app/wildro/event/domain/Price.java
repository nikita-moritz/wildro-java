package app.wildro.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What taking part costs, as the source states it: the regular adult price, zero
 * when free. Other prices stay in the description. Absent when the source says
 * nothing, never guessed (V6 holds the same rules).
 */
@Embeddable
public class Price {

	private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

	private static final BigDecimal MAX = new BigDecimal("999999.99");

	@Column(name = "price_amount")
	private BigDecimal amount;

	@Column(name = "price_currency")
	private String currency;

	protected Price() {
		// for JPA
	}

	/** @throws InvalidEventException naming the part of the price that is wrong. */
	public Price(BigDecimal amount, String currency) {
		if (amount == null) {
			throw new InvalidEventException("amount", "is required");
		}
		if (amount.signum() < 0 || amount.compareTo(MAX) > 0) {
			throw new InvalidEventException("amount", "must be between 0 and " + MAX);
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new InvalidEventException("amount", "has at most two decimals");
		}
		if (currency == null || !CURRENCY.matcher(currency).matches()) {
			throw new InvalidEventException("currency", "must be a three-letter code such as EUR");
		}
		this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
		this.currency = currency;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public String getCurrency() {
		return this.currency;
	}

	public boolean isFree() {
		return this.amount.signum() == 0;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Price that && this.amount.compareTo(that.amount) == 0
				&& Objects.equals(this.currency, that.currency);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.amount.stripTrailingZeros(), this.currency);
	}

}
