package org.archive.spider.core;

public class Node {
    public final int type;
    public final String name;
    public final Object data;

    public Node(int type, String name, Object data) {
        this.type = type;
        this.name = name;
        this.data = data;
    }

    public Node(String name, Object data) {
        this(TYPE_FILE, name, data);
    }

    @Override
    public String toString() {
        return "Node{" +
                "type=" + stringType() +
                ", name='" + name + '\'' +
                ", data=" + data +
                '}';
    }

    private String stringType() {
        if (type == TYPE_DIRECTORY) {
            return  "D";
        }
        return "F";
    }

    public static final int TYPE_FILE = 1;
    public static final int TYPE_DIRECTORY = 2;
}
