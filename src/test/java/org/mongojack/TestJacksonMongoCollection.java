/*
 * Copyright 2011 VZ Netzwerke Ltd
 * Copyright 2014 devbliss GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mongojack;

import com.mongodb.WriteConcern;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mongojack.mock.MockEmbeddedObject;
import org.mongojack.mock.MockObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.*;

public class TestJacksonMongoCollection extends MongoDBTestBase {
    private JacksonMongoCollection<MockObject> coll;

    @Before
    public void setup() throws Exception {
        coll = JacksonMongoCollection.<MockObject> builder().build(getMongoCollection("testJacksonMongoCollection"), MockObject.class);
    }

    @Test
    public void testQuery() {
        MockObject o1 = new MockObject("1", "ten", 10);
        MockObject o2 = new MockObject("2", "ten", 10);
        o2.object = getMockEmbeddedObject();
        o2.complexList = new ArrayList<>();
        o2.complexList.add(getMockEmbeddedObject());
        coll.insert(o1, o2, new MockObject("twenty", 20));

        List<MockObject> results = coll
                .find(new Document("string", "ten")).into(new ArrayList<>());
        assertThat(results, hasSize(2));
        assertThat(results, contains(o1, o2));
    }

    private MockEmbeddedObject getMockEmbeddedObject() {
        final MockEmbeddedObject eo = new MockEmbeddedObject("foo");
        eo.date = new Date();
        eo.calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        eo.objectId = new ObjectId();
        return eo;
    }

    @Test
    public void testRemove() {
        coll.insert(new MockObject("ten", 10));
        coll.insert(new MockObject("ten", 100));
        MockObject object = new MockObject("1", "twenty", 20);
        coll.insert(object);

        coll.remove(new Document("string", "ten"));

        List<MockObject> remaining = coll.find().into(new ArrayList<>());
        assertThat(remaining, Matchers.hasSize(1));
        assertThat(remaining, contains(object));
    }

    @Test
    public void testRemoveById() {
        coll.insert(new MockObject("id1", "ten", 10));
        coll.insert(new MockObject("id2", "ten", 100));
        MockObject object = new MockObject("id3", "twenty", 20);
        coll.insert(object);

        coll.removeById("id3");

        List<MockObject> remaining = coll.find().into(new ArrayList<>());
        assertThat(remaining, Matchers.hasSize(2));
        assertThat(remaining, not(contains(object)));
    }

    @Test
    public void testFindAndModifyWithBuilder() {
        coll.insert(new MockObject("id1", "ten", 10));
        coll.insert(new MockObject("id2", "ten", 10));

        MockObject mockObject3 = new MockObject("id3", "ten", 10);
        mockObject3.simpleList = new ArrayList<>();
        mockObject3.simpleList.add("a");
        mockObject3.simpleList.add("b");
        coll.insert(mockObject3);

        MockObject result1 = coll.findAndModify(DBQuery.is("_id", "id1"), null,
                null, DBUpdate.set("integer", 20)
                        .set("string", "twenty"), true, false);
        assertThat(result1.integer, equalTo(20));
        assertThat(result1.string, equalTo("twenty"));

        MockObject result2 = coll.findAndModify(DBQuery.is("_id", "id2"), null,
                null, DBUpdate.set("integer", 30)
                        .set("string", "thirty"), true, false);
        assertThat(result2.integer, equalTo(30));
        assertThat(result2.string, equalTo("thirty"));

        MockObject result3 = coll.findAndModify(DBQuery.is("_id", "id3"), null,
                null, DBUpdate.pushAll("simpleList", Arrays.asList("1", "2", "3")),
                true, false);
        assertThat(result3.simpleList, hasSize(5));
        assertThat(result3.simpleList, hasItems("1", "2", "3"));

        coll.removeById("id1");
        coll.removeById("id2");
        coll.removeById("id3");
    }

    @Test
    public void testReplaceOneByNonIdQuery() {
        coll.insert(new MockObject("id1", "ten", 10));

        coll.replaceOne(DBQuery.is("string", "ten"),
                new MockObject("id1", "twenty", 20),
                /*upsert*/ false,
                WriteConcern.W1);

        MockObject found = coll.findOne(DBQuery.is("_id", "id1"));

        assertThat(found, equalTo(new MockObject("id1", "twenty", 20)));
    }

    @Test
    public void testReplaceOneByUsesQueryNotId() {
        coll.insert(new MockObject("id1", "ten", 10));

        coll.replaceOne(DBQuery.is("string", "ten"),
                new MockObject(null, "twenty", 20),
                /*upsert*/ false,
                WriteConcern.W1);

        MockObject found = coll.findOne(DBQuery.is("_id", "id1"));

        assertThat(found, equalTo(new MockObject("id1", "twenty", 20)));
    }

    @Test
    public void testReplaceOneUpsertsIfNoDocumentExistsByQueryAndUpsertTrue() {
        coll.replaceOne(DBQuery.is("string", "ten"),
                new MockObject(null, "twenty", 20),
                /*upsert*/ true,
                WriteConcern.W1);

        MockObject found = coll.findOne(DBQuery.is("string", "twenty"));

        assertThat(found, equalTo(new MockObject(found._id, "twenty", 20)));
    }

    @Test
    public void testReplaceOneDoesNotUpsertIfUpsertFalse() {
        coll.replaceOne(DBQuery.is("string", "ten"),
                new MockObject(null, "twenty", 20),
                /*upsert*/ false,
                WriteConcern.W1);

        MockObject found = coll.findOne(DBQuery.is("string", "twenty"));

        assertThat(found, nullValue());
    }

    @Test
    public void testReplaceOneByIdUsesIdProvided() {
        coll.insert(new MockObject("id1", "ten", 10));

        coll.replaceOneById("id1", new MockObject(null, "twenty", 20));

        MockObject found = coll.findOne(DBQuery.is("_id", "id1"));

        assertThat(found, equalTo(new MockObject("id1", "twenty", 20)));
    }
}
