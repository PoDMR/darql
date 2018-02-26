package com.gitlab.ctt.arq.utilx;

import java.util.ArrayList;


public class RingBuffer<T> {
	private ArrayList<T> arrayList;
	private int head = 0;
	private int count = 0;
	private int capacity;

	public RingBuffer(int capacity) {
		this.capacity = capacity;
		arrayList = new ArrayList<>(capacity);
	}

	public boolean add(T item) {
		if (count < capacity) {
			count++;
			arrayList.add(null);
		} else {
			head++;
		}
		set(count - 1, item);
		return true;
	}

	public T removeFirst() {
		return remove(0);
	}

	public T remove(int i) {
		T item = get(i);
		head = ++head % capacity;
		count--;
		return item;
	}

	public int size() {
		return count;
	}

	public T get(int i) {
		return arrayList.get(lookup(i));
	}

	private int lookup(int i) {
		return (head + i) % capacity;
	}

	public void set(int i, T item) {
		arrayList.set(lookup(i), item);
	}
}
