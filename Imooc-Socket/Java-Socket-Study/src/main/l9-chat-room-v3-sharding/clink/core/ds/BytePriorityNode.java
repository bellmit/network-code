package clink.core.ds;

/**
 * 带优先级的节点, 可用于构成链表。
 *
 * @author Ztiany
 * Email ztiany3@gmail.com
 * Date 2018/11/25 23:04
 */
public class BytePriorityNode<Item> {

    /**
     * 优先级
     */
    public byte priority;

    /**
     * 数据
     */
    public Item item;

    /**
     * 下一跳
     */
    public BytePriorityNode<Item> next;

    public BytePriorityNode(Item item) {
        this.item = item;
    }

    /**
     * 拆入优先级链表中，根据 BytePriorityNode 的 priority 插入到合适的位置。
     */
    public void appendWithPriority(BytePriorityNode<Item> node) {
        if (next == null) {
            next = node;
        } else {
            BytePriorityNode<Item> after = this.next;
            //根据优先级添加
            if (after.priority < node.priority) {
                // 中间位置插入
                this.next = node;
                node.next = after;
            } else {
                //往后插入
                after.appendWithPriority(node);
            }
        }
    }

}
