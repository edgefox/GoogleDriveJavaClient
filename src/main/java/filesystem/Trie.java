package filesystem;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of Trie data structure.
 * See more at http://en.wikipedia.org/wiki/Trie
 *
 * @author Ivan Lyutov
 */
public class Trie<K, M> implements Serializable {
    private K key;
    private M model;
    private Trie<K, M> parent;
    private Map<K, Trie<K, M>> children;

    public Trie() {
        this(null, null, null);
    }

    public Trie(K key, M model) {
        this.key = key;
        this.model = model;
        this.parent = null;
        this.children = new HashMap<K, Trie<K, M>>();
    }

    Trie(K key, M model, Trie<K, M> parent) {
        this.key = key;
        this.model = model;
        this.parent = parent;
        this.children = new HashMap<K, Trie<K, M>>();
    }

    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    public M getModel() {
        return model;
    }

    public void setModel(M model) {
        this.model = model;
    }

    public Trie<K, M> getParent() {
        return parent;
    }

    public Map<K, Trie<K, M>> getChildren() {
        return children;
    }

    public void removeChildren() {
        children.clear();
    }

    protected Trie<K, M> detachFromParent() {
        Trie<K, M> oldParent = parent;
        if (parent != null) {
            this.parent.removeChild(this.getKey());
            this.parent = null;
        }
        return oldParent;
    }

    public Trie<K, M> getChild(K key) {
        return children.get(key);
    }

    public Trie<K, M> addChild(K key, M model) {
        Trie<K, M> child = children.get(key);
        if (child == null) {
            children.put(key, new Trie<K, M>(key, model));
        } else {
            child.setModel(model);
        }
        return child;
    }

    public Trie<K, M> addChild(Trie<K, M> node) {
        node.parent = this;
        children.put(node.getKey(), node);
        return node;
    }

    public Trie<K, M> removeChild(K key) {
        return children.remove(key);
    }

    public Trie<K, M> removeChild(Trie<K, M> node) {
        return children.remove(node.getKey());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trie)) return false;

        Trie trie = (Trie) o;
        return new EqualsBuilder().append(key, trie.key).
                append(model, trie.model).
                append(children, trie.children).build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(key).
                append(model).
                append(children).build();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("key", key)
                .append("model", model)
                .append("children", children)
                .build();
    }
}
