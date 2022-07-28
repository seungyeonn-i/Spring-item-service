package hello.itemservice.domain.item;

import lombok.Data; //위험함
import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class Item {
    private Long id;
    private String itemName;
    private Integer price;  //integer쓰는 이유 null 대비
    private Integer quantity;

    public Item() {
    }

    public Item(String itemName, Integer price, Integer quantity) {
        this.itemName = itemName;
        this.price = price;
        this.quantity = quantity;
    }




}
