package app.wildro.community;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.oauth2.jwt.Jwt;

import app.wildro.community.people.PeopleDirectory;
import app.wildro.community.people.Person;

/** The user service in memory. Anyone not set up is named after their id and accepted the terms. */
public class FakePeople implements PeopleDirectory {

	private final Map<String, Person> people = new ConcurrentHashMap<>();

	public void put(Person person) {
		this.people.put(person.id(), person);
	}

	public void reset() {
		this.people.clear();
	}

	@Override
	public Person whoIs(Jwt token) {
		return this.people.getOrDefault(token.getSubject(), new Person(token.getSubject(), "Name " + token.getSubject(), true));
	}

}
