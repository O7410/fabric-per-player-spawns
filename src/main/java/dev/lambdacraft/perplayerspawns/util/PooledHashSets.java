package dev.lambdacraft.perplayerspawns.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.lang.ref.WeakReference;
import java.util.Iterator;

/** @author Spottedleaf */
public class PooledHashSets<E> {

    // we really want to avoid that equals() check as much as possible...
    protected final Object2ObjectOpenHashMap<PooledObjectLinkedOpenHashSet<E>, PooledObjectLinkedOpenHashSet<E>> mapPool = new Object2ObjectOpenHashMap<>(64, 0.25f);

    protected void decrementReferenceCount(final PooledObjectLinkedOpenHashSet<E> current) {
        if (current.referenceCount == 0) {
            throw new IllegalStateException("Cannot decrement reference count for " + current);
        }
        if (current.referenceCount == -1 || --current.referenceCount > 0) {
            return;
        }

        this.mapPool.remove(current);
    }

    public PooledObjectLinkedOpenHashSet<E> findMapWith(final PooledObjectLinkedOpenHashSet<E> current, final E object) {
        final PooledObjectLinkedOpenHashSet<E> cached = current.getAddCache(object);

        if (cached != null) {
            if (cached.referenceCount != -1) {
                ++cached.referenceCount;
            }

            decrementReferenceCount(current);

            return cached;
        }

        if (!current.add(object)) {
            return current;
        }

        // we use get/put since we use a different key on put
        PooledObjectLinkedOpenHashSet<E> ret = this.mapPool.get(current);

        if (ret == null) {
            ret = new PooledObjectLinkedOpenHashSet<>(current);
            current.remove(object);
            this.mapPool.put(ret, ret);
            ret.referenceCount = 1;
        } else {
            if (ret.referenceCount != -1) {
                ++ret.referenceCount;
            }
            current.remove(object);
        }

        current.updateAddCache(object, ret);

        decrementReferenceCount(current);
        return ret;
    }

    // rets null if current.size() == 1
    public PooledObjectLinkedOpenHashSet<E> findMapWithout(final PooledObjectLinkedOpenHashSet<E> current, final E object) {
        if (current.set.size() == 1) {
            decrementReferenceCount(current);
            return null;
        }

        final PooledObjectLinkedOpenHashSet<E> cached = current.getRemoveCache(object);

        if (cached != null) {
            if (cached.referenceCount != -1) {
                ++cached.referenceCount;
            }

            decrementReferenceCount(current);

            return cached;
        }

        if (!current.remove(object)) {
            return current;
        }

        // we use get/put since we use a different key on put
        PooledObjectLinkedOpenHashSet<E> ret = this.mapPool.get(current);

        if (ret == null) {
            ret = new PooledObjectLinkedOpenHashSet<>(current);
            current.add(object);
            this.mapPool.put(ret, ret);
            ret.referenceCount = 1;
        } else {
            if (ret.referenceCount != -1) {
                ++ret.referenceCount;
            }
            current.add(object);
        }

        current.updateRemoveCache(object, ret);

        decrementReferenceCount(current);
        return ret;
    }

    public static final class PooledObjectLinkedOpenHashSet<E> implements Iterable<E> {

        private static final WeakReference NULL_REFERENCE = new WeakReference(null);

        final ObjectLinkedOpenHashSet<E> set;
        int referenceCount; // -1 if special
        int hash; // optimize hashcode

        // add cache
        WeakReference<E> lastAddObject = NULL_REFERENCE;
        WeakReference<PooledObjectLinkedOpenHashSet<E>> lastAddMap = NULL_REFERENCE;

        // remove cache
        WeakReference<E> lastRemoveObject = NULL_REFERENCE;
        WeakReference<PooledObjectLinkedOpenHashSet<E>> lastRemoveMap = NULL_REFERENCE;

        public PooledObjectLinkedOpenHashSet() {
            this.set = new ObjectLinkedOpenHashSet<>(2, 0.6f);
        }

        public PooledObjectLinkedOpenHashSet(final E single) {
            this();
            this.referenceCount = -1;
            this.add(single);
        }

        public PooledObjectLinkedOpenHashSet(final PooledObjectLinkedOpenHashSet<E> other) {
            this.set = other.set.clone();
            this.hash = other.hash;
        }

        // from https://github.com/Spottedleaf/ConcurrentUtil/blob/master/src/main/java/ca/spottedleaf/concurrentutil/util/IntegerUtil.java
        // generated by https://github.com/skeeto/hash-prospector
        static int hash0(int x) {
            x *= 0x36935555;
            x ^= x >>> 16;
            return x;
        }

        public PooledObjectLinkedOpenHashSet<E> getAddCache(final E element) {
            final E currentAdd = this.lastAddObject.get();

            if (currentAdd == null || !(currentAdd == element || currentAdd.equals(element))) {
                return null;
            }

            final PooledObjectLinkedOpenHashSet<E> map = this.lastAddMap.get();
            if (map == null || map.referenceCount == 0) {
                // we need to ret null if ref count is zero as calling code will assume the map is in use
                return null;
            }

            return map;
        }

        public PooledObjectLinkedOpenHashSet<E> getRemoveCache(final E element) {
            final E currentRemove = this.lastRemoveObject.get();

            if (currentRemove == null || !(currentRemove == element || currentRemove.equals(element))) {
                return null;
            }

            final PooledObjectLinkedOpenHashSet<E> map = this.lastRemoveMap.get();
            if (map == null || map.referenceCount == 0) {
                // we need to ret null if ref count is zero as calling code will assume the map is in use
                return null;
            }

            return map;
        }

        public void updateAddCache(final E element, final PooledObjectLinkedOpenHashSet<E> map) {
            this.lastAddObject = new WeakReference<>(element);
            this.lastAddMap = new WeakReference<>(map);
        }

        public void updateRemoveCache(final E element, final PooledObjectLinkedOpenHashSet<E> map) {
            this.lastRemoveObject = new WeakReference<>(element);
            this.lastRemoveMap = new WeakReference<>(map);
        }

        boolean add(final E element) {
            boolean added =  this.set.add(element);

            if (added) {
                this.hash += hash0(element.hashCode());
            }

            return added;
        }

        boolean remove(Object element) {
            boolean removed = this.set.remove(element);

            if (removed) {
                this.hash -= hash0(element.hashCode());
            }

            return removed;
        }

        @Override
        public Iterator<E> iterator() {
            return this.set.iterator();
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof PooledObjectLinkedOpenHashSet)) {
                return false;
            }
            if (this.referenceCount == 0) {
                return other == this;
            } else {
                if (other == this) {
                    // Unfortunately we are never equal to our own instance while in use!
                    return false;
                }
                return this.hash == ((PooledObjectLinkedOpenHashSet)other).hash && this.set.equals(((PooledObjectLinkedOpenHashSet)other).set);
            }
        }

        @Override
        public String toString() {
            return "PooledHashSet: size: " + this.set.size() + ", reference count: " + this.referenceCount + ", hash: " +
                    this.hashCode() + ", identity: " + System.identityHashCode(this) + " map: " + this.set.toString();
        }
    }
}