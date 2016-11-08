package rest.addressbook;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import rest.addressbook.config.ApplicationConfig;
import rest.addressbook.domain.AddressBook;
import rest.addressbook.domain.Person;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * A simple test suite
 *
 */
public class AddressBookServiceTest {

	private HttpServer server;

    @Test
    public void serviceIsAlive() throws IOException {
        // Prepare server
        AddressBook ab = new AddressBook();
        int initialAddressBookSize = ab.getPersonList().size();
        launchServer(ab);

        final Client CLIENT = ClientBuilder.newClient();

        // Request the address book
        AddressBook addressBook = requestAddressBook(CLIENT);
        assertEquals(0, addressBook.getPersonList().size());

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // test that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////

        AddressBook newAddressBook = requestAddressBook(CLIENT);
        // Safety check: the method must not modify a resource
        assertEquals(initialAddressBookSize, ab.getPersonList().size());
        // Idempotency check: subsequent requests must get the same results
        assertEquals(addressBook.getPersonList(), newAddressBook.getPersonList());
    }

    private AddressBook requestAddressBook(Client client) {
        Response response = client.target("http://localhost:8282/contacts").request().get();
        AddressBook addressBook = response.readEntity(AddressBook.class);
        assertEquals(200, response.getStatus());
        return addressBook;
    }

    @Test
    public void createUser() throws IOException {
        // Prepare server
        AddressBook ab = new AddressBook();
        launchServer(ab);

        // Prepare data
        Person juan = new Person();
        juan.setName("Juan");
        int juanId = 1;
        URI juanURI = URI.create("http://localhost:8282/contacts/person/" + juanId);

        final Client CLIENT = ClientBuilder.newClient();

        // Create a new user
        Person juanUpdated = createPerson(CLIENT, juan);
        assertEquals(juan.getName(), juanUpdated.getName());
        assertEquals(juanId, juanUpdated.getId());
        assertEquals(juanURI, juanUpdated.getHref());

        // Check that the new user exists
        juanUpdated = requestPerson(CLIENT, juanId);
        assertEquals(juan.getName(), juanUpdated.getName());
        assertEquals(juanId, juanUpdated.getId());
        assertEquals(juanURI, juanUpdated.getHref());

        //////////////////////////////////////////////////////////////////////
        // Verify that POST /contacts is well implemented by the service, i.e
        // test that it is not safe and not idempotent
        //////////////////////////////////////////////////////////////////////

        int initialAddressBookSize = ab.getPersonList().size();
        Person maria = new Person();
        maria.setName("Maria");
        Person mariaUpated = createPerson(CLIENT, maria);
        // Unsafety check: the method must modify the system's resources
        assertNotEquals(initialAddressBookSize, ab.getPersonList().size());
        // Non-idempotency check: subsequent requests must return different results
        Person mariaNewUpdated = createPerson(CLIENT, maria);
        assertNotEquals(mariaUpated.getId(), mariaNewUpdated.getId());
    }

    private Person createPerson(Client client, Person person) {
        Response response = client.target("http://localhost:8282/contacts")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(person, MediaType.APPLICATION_JSON));
        assertEquals(201, response.getStatus());
        //assertEquals(personURI, response.getLocation());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        Person personUpdated = response.readEntity(Person.class);
        return personUpdated;
    }

    private Person requestPerson(Client client, int personId) {
        Response response = client.target("http://localhost:8282/contacts/person/" + personId)
                .request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        Person person = response.readEntity(Person.class);
        return person;
    }

	@Test
	public void createUsers() throws IOException {
		// Prepare server
		AddressBook ab = new AddressBook();
		Person salvador = new Person();
		salvador.setName("Salvador");
		salvador.setId(ab.nextId());
		ab.getPersonList().add(salvador);
		launchServer(ab);

		final Client CLIENT = ClientBuilder.newClient();

		// Prepare data
		Person juan = new Person();
		juan.setName("Juan");
		int juanId = 2;
		URI juanURI = URI.create("http://localhost:8282/contacts/person/" + juanId);
		Person maria = new Person();
		maria.setName("Maria");
		int mariaId = 3;
		URI mariaURI = URI.create("http://localhost:8282/contacts/person/" + mariaId);

		// Create a user
		createPerson(CLIENT, juan);

		// Create a second user
		Person mariaUpdated = createPerson(CLIENT, maria);
		assertEquals(maria.getName(), mariaUpdated.getName());
		assertEquals(mariaId, mariaUpdated.getId());
		assertEquals(mariaURI, mariaUpdated.getHref());

		// Check that the new user exists
		mariaUpdated = requestPerson(CLIENT, mariaId);
		assertEquals(maria.getName(), mariaUpdated.getName());
		assertEquals(mariaId, mariaUpdated.getId());
		assertEquals(mariaURI, mariaUpdated.getHref());

		//////////////////////////////////////////////////////////////////////
		// Verify that GET /contacts/person/3 is well implemented by the service, i.e
		// test that it is safe and idempotent
		//////////////////////////////////////////////////////////////////////

		Person initialMaria = ab.getPersonList().get(mariaId - 1);
		Person newMaria = requestPerson(CLIENT, mariaId);
		// Safety check: the method must not modify a resource
		assertEquals(initialMaria.getId(), newMaria.getId());
		// Idempotency check: subsequent requests must get the same results
		assertEquals(mariaUpdated.getId(), newMaria.getId());
	}

	@Test
	public void listUsers() throws IOException {

		// Prepare server
		AddressBook ab = new AddressBook();
		Person salvador = new Person();
		salvador.setName("Salvador");
		Person juan = new Person();
		juan.setName("Juan");
		ab.getPersonList().add(salvador);
		ab.getPersonList().add(juan);
		launchServer(ab);

		// Test list of contacts
		Client client = ClientBuilder.newClient();
		Response response = client.target("http://localhost:8282/contacts")
				.request(MediaType.APPLICATION_JSON).get();
		assertEquals(200, response.getStatus());
		assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
		AddressBook addressBookRetrieved = response
				.readEntity(AddressBook.class);
		assertEquals(2, addressBookRetrieved.getPersonList().size());
		assertEquals(juan.getName(), addressBookRetrieved.getPersonList()
				.get(1).getName());

		//////////////////////////////////////////////////////////////////////
		// Verify that GET for collections is well implemented by the service, i.e
		// test that it is safe and idempotent
		//////////////////////////////////////////////////////////////////////	
	
	}

	@Test
	public void updateUsers() throws IOException {
		// Prepare server
		AddressBook ab = new AddressBook();
		Person salvador = new Person();
		salvador.setName("Salvador");
		salvador.setId(ab.nextId());
		Person juan = new Person();
		juan.setName("Juan");
		juan.setId(ab.getNextId());
		URI juanURI = URI.create("http://localhost:8282/contacts/person/2");
		ab.getPersonList().add(salvador);
		ab.getPersonList().add(juan);
		launchServer(ab);

		// Update Maria
		Person maria = new Person();
		maria.setName("Maria");
		Client client = ClientBuilder.newClient();
		Response response = client
				.target("http://localhost:8282/contacts/person/2")
				.request(MediaType.APPLICATION_JSON)
				.put(Entity.entity(maria, MediaType.APPLICATION_JSON));
		assertEquals(200, response.getStatus());
		assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
		Person juanUpdated = response.readEntity(Person.class);
		assertEquals(maria.getName(), juanUpdated.getName());
		assertEquals(2, juanUpdated.getId());
		assertEquals(juanURI, juanUpdated.getHref());

		// Verify that the update is real
		response = client.target("http://localhost:8282/contacts/person/2")
				.request(MediaType.APPLICATION_JSON).get();
		assertEquals(200, response.getStatus());
		assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
		Person mariaRetrieved = response.readEntity(Person.class);
		assertEquals(maria.getName(), mariaRetrieved.getName());
		assertEquals(2, mariaRetrieved.getId());
		assertEquals(juanURI, mariaRetrieved.getHref());

		// Verify that only can be updated existing values
		response = client.target("http://localhost:8282/contacts/person/3")
				.request(MediaType.APPLICATION_JSON)
				.put(Entity.entity(maria, MediaType.APPLICATION_JSON));
		assertEquals(400, response.getStatus());

		//////////////////////////////////////////////////////////////////////
		// Verify that PUT /contacts/person/2 is well implemented by the service, i.e
		// test that it is idempotent
		//////////////////////////////////////////////////////////////////////	
	
	}

	@Test
	public void deleteUsers() throws IOException {
		// Prepare server
		AddressBook ab = new AddressBook();
		Person salvador = new Person();
		salvador.setName("Salvador");
		salvador.setId(1);
		Person juan = new Person();
		juan.setName("Juan");
		juan.setId(2);
		ab.getPersonList().add(salvador);
		ab.getPersonList().add(juan);
		launchServer(ab);

		// Delete a user
		Client client = ClientBuilder.newClient();
		Response response = client
				.target("http://localhost:8282/contacts/person/2").request()
				.delete();
		assertEquals(204, response.getStatus());

		// Verify that the user has been deleted
		response = client.target("http://localhost:8282/contacts/person/2")
				.request().delete();
		assertEquals(404, response.getStatus());

		//////////////////////////////////////////////////////////////////////
		// Verify that DELETE /contacts/person/2 is well implemented by the service, i.e
		// test that it is idempotent
		//////////////////////////////////////////////////////////////////////	

	}

	@Test
	public void findUsers() throws IOException {
		// Prepare server
		AddressBook ab = new AddressBook();
		Person salvador = new Person();
		salvador.setName("Salvador");
		salvador.setId(1);
		Person juan = new Person();
		juan.setName("Juan");
		juan.setId(2);
		ab.getPersonList().add(salvador);
		ab.getPersonList().add(juan);
		launchServer(ab);

		// Test user 1 exists
		Client client = ClientBuilder.newClient();
		Response response = client
				.target("http://localhost:8282/contacts/person/1")
				.request(MediaType.APPLICATION_JSON).get();
		assertEquals(200, response.getStatus());
		assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
		Person person = response.readEntity(Person.class);
		assertEquals(person.getName(), salvador.getName());
		assertEquals(person.getId(), salvador.getId());
		assertEquals(person.getHref(), salvador.getHref());

		// Test user 2 exists
		response = client.target("http://localhost:8282/contacts/person/2")
				.request(MediaType.APPLICATION_JSON).get();
		assertEquals(200, response.getStatus());
		assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
		person = response.readEntity(Person.class);
		assertEquals(person.getName(), juan.getName());
		assertEquals(2, juan.getId());
		assertEquals(person.getHref(), juan.getHref());

		// Test user 3 exists
		response = client.target("http://localhost:8282/contacts/person/3")
				.request(MediaType.APPLICATION_JSON).get();
		assertEquals(404, response.getStatus());
	}

	private void launchServer(AddressBook ab) throws IOException {
		URI uri = UriBuilder.fromUri("http://localhost/").port(8282).build();
		server = GrizzlyHttpServerFactory.createHttpServer(uri,
				new ApplicationConfig(ab));
		server.start();
	}

	@After
	public void shutdown() {
		if (server != null) {
			server.shutdownNow();
		}
		server = null;
	}

}
