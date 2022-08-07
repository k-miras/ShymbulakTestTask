package kz.shymbulak.mirastest

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.util.*

@Document(collection = "cartItem")
data class CartItem(
    @DocumentReference(collection = "product")
    val product: Product,
    @DocumentReference(collection = "order")
    val order: Order,
    @JsonProperty var quantity: Int
) {
    @Id
    @JsonProperty
    lateinit var id: String
}

@Repository
interface CartItemRepository : MongoRepository<CartItem, String> {
    override fun findAll(): MutableList<CartItem>
    override fun deleteById(id: String)
    override fun findById(id: String): Optional<CartItem>
    override fun <S : CartItem> insert(entity: S): S
    override fun <S : CartItem> save(entity: S): S

    fun findByOrderAndProduct(order: Order, product: Product): MutableList<CartItem>
    fun findAllByOrder(order: Order): MutableList<CartItem>
    fun deleteByOrder(order: Order)
    fun deleteByProduct(product: Product)
}

@Service
class CartItemService {
    @Autowired
    private lateinit var repo: CartItemRepository

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var productsService: ProductsService

    fun upsertProductToActiveOrder(productId: String, userId: String, quantity: Int): Optional<CartItem> {
        val product = productsService.retrieve(productId)
        if (!product.isPresent) return Optional.empty()

        val order = orderService.retrieveActiveByUser(userId)
        if (!order.isPresent) return Optional.empty()

        if (quantity == 0) {
            this.delete(product.get(), order.get())
            return Optional.empty()
        }

        val cartItems = repo.findByOrderAndProduct(order.get(), product.get())

        when (cartItems.size) {
            0 -> return Optional.of(repo.insert(CartItem(product.get(), order.get(), quantity)))

            1 -> {
                cartItems.first().quantity += quantity
                return Optional.of(repo.save(cartItems.first()))
            }

            in 2..Int.MAX_VALUE -> {
                var quantityToInsert = 0

                for (item in cartItems) {
                    quantityToInsert += item.quantity
                }
                quantityToInsert += quantity

                this.delete(product.get(), order.get())

                return Optional.of(repo.insert(CartItem(product.get(), order.get(), quantityToInsert)))
            }
        }

        return Optional.empty()
    }

    fun retrieveItemsOfActiveOrder(userId: String): MutableList<CartItem> {
        val order = orderService.retrieveActiveByUser(userId)
        if (!order.isPresent) return mutableListOf()

        return this.retrieveItemsOfOrder(order.get())
    }

    //use later - calculate
    fun retrieveItemsOfOrder(order: Order): MutableList<CartItem> {
        return repo.findAllByOrder(order)
    }


    fun deleteProductOfActiveOrder(productId: String, userId: String) {
        val product = productsService.retrieve(productId)
        if (!product.isPresent) return

        val order = orderService.retrieveActiveByUser(userId)
        if (!order.isPresent) return

        this.delete(product.get(), order.get())
    }

    private fun delete(product: Product, order: Order) {
        for (item in repo.findByOrderAndProduct(order, product)) {
            repo.deleteById(item.id)
        }
    }

    fun findAll(): MutableList<CartItem> {
        return repo.findAll()
    }
}


@RestController
@RequestMapping("cartItem")
class CartItemController {
    @Autowired
    private lateinit var service: CartItemService

    @GetMapping
    fun findAll(): MutableList<CartItem> {
        return service.findAll()
    }

    @PostMapping("/{userId}")
    fun addToCart(@RequestBody dto: AddToCartDTO, @PathVariable userId: String): Optional<CartItem> {
        return service.upsertProductToActiveOrder(dto.productId, userId, dto.quantity)
    }

    @DeleteMapping("/{userId}")
    fun deleteProductOfActiveOrder(@RequestBody dto: DeleteFromCartDTO, @PathVariable userId: String) {
        service.deleteProductOfActiveOrder(dto.productId, userId)
    }

    @GetMapping("/{userId}")
    fun findCartOfuser(@PathVariable userId: String): CalculatedCartDTO {
        return CalculatedCartDTO(service.retrieveItemsOfActiveOrder(userId))
    }
}

data class DeleteFromCartDTO(val productId: String)
data class AddToCartDTO(val productId: String, val quantity: Int)
data class CalculatedCartDTO(val items: MutableList<CartItem>) {
    val totalPrice: Int
        get() {
            var total: Int = 0
            for (item in this.items) {
                total += item.quantity * item.product.price
            }
            return total
        }
}