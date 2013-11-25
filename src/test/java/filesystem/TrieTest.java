package filesystem;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:15 PM
 */
public class TrieTest {
    private Trie<String, FileMetadata> rootTrie;
    private Set<Trie<String, FileMetadata>> children = new HashSet<>();

    @Before
    public void setUp() throws Exception {
        rootTrie = new Trie<>("root", new FileMetadata("root", "title", false, "checkSum"));
        for (int i = 0; i < 10; i++) {
            Trie<String, FileMetadata> child = new Trie<>(String.format("child_id_%d", i),
                                                          new FileMetadata(String.format("child_id_%d", i),
                                                                           String.format("child_title_%d", i),
                                                                           false,
                                                                           UUID.randomUUID().toString()));
            rootTrie.addChild(child);
            children.add(child);
        }
    }

    @Test
    public void testGetKey() throws Exception {
        assertEquals("root", rootTrie.getKey());
    }

    @Test
    public void testSetKey() throws Exception {
        rootTrie.setKey("id_set");
        assertEquals("id_set", rootTrie.getKey());
    }

    @Test
    public void testGetModel() throws Exception {
        FileMetadata model = rootTrie.getModel();
        assertNotNull(model);
    }

    @Test
    public void testSetModel() throws Exception {
        FileMetadata model = new FileMetadata("id_set", "title_set", true, "checkSum_set");
        rootTrie.setModel(model);
        assertEquals(model, rootTrie.getModel());
    }

    @Test
    public void testGetParent() throws Exception {
        Trie<String, FileMetadata> parent = rootTrie.getParent();
        assertNull(parent);
        Trie<String, FileMetadata> childTrie = rootTrie.getChildren().get("child_id_1");
        assertEquals(rootTrie, childTrie.getParent());
    }

    @Test
    public void testGetChildren() throws Exception {
        assertNotNull(rootTrie.getChildren());
        assertEquals(children.size(), rootTrie.getChildren().size());
        assertTrue(rootTrie.getChildren().values().containsAll(children));
    }

    @Test
    public void testRemoveChildren() throws Exception {
        rootTrie.removeChildren();
        assertTrue(rootTrie.getChildren().isEmpty());
    }

    @Test
    public void testDetachFromParent() throws Exception {
        Trie<String, FileMetadata> child = rootTrie.getChildren().get("child_id_1");
        child.detachFromParent();
        assertNull(child.getParent());
    }

    @Test
    public void testGetChild() throws Exception {
        Trie<String, FileMetadata> child = rootTrie.getChild("child_id_1");
        assertNotNull(child);
    }

    @Test
    public void testAddChild() throws Exception {
        Trie<String, FileMetadata> newChild = rootTrie.addChild(new Trie<>(UUID.randomUUID().toString(),
                                                                           new FileMetadata(UUID.randomUUID().toString(),
                                                                                            UUID.randomUUID().toString(),
                                                                                            true,
                                                                                            UUID.randomUUID().toString())));
        assertEquals(newChild, rootTrie.getChild(newChild.getKey()));
    }

    @Test
    public void testRemoveChild() throws Exception {
        rootTrie.removeChild("child_id_1");
        assertNull(rootTrie.getChild("child_id_1"));
    }
}
