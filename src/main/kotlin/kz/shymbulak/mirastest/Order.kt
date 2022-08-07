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

@Document(collection = "order")
data class Order(
    @DocumentReference(collection = "user")
    val user: User,
    @JsonProperty(defaultValue = false.toString())
    var isPaid: Boolean
) {
    @Id
    @JsonProperty
    lateinit var id: String
}


@Repository
interface OrderRepository : MongoRepository<Order, String> {
    override fun findAll(): MutableList<Order>
    fun deleteByUser(user: User)
    fun findByUserAndIsPaidFalse(user: User): Optional<Order>
    fun findAllByUserAndIsPaidTrue(user: User): MutableList<Order>
    override fun <S : Order> insert(entity: S): S
    override fun <S : Order> save(entity: S): S
}

@Service
class OrderService {
    @Autowired
    private lateinit var repo: OrderRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var cartItemRepository: CartItemRepository

    fun create(userId: String): Optional<Order> {
        val user = userRepository.findById(userId)
        if (!user.isPresent) return Optional.empty()

        if (repo.findByUserAndIsPaidFalse(user.get()).isPresent) return Optional.empty()

        return Optional.of(repo.insert(Order(user.get(), false)))
    }

    fun retrieveActiveByUser(userId: String): Optional<Order> {
        val user = userRepository.findById(userId)
        if (!user.isPresent) return Optional.empty()

        val order = repo.findByUserAndIsPaidFalse(user.get())
        if (order.isPresent) return order

        return this.create(userId)
    }

    fun retrievePaidByUser(userId: String): MutableList<Order> {
        val user = userRepository.findById(userId)
        if (!user.isPresent) return mutableListOf()

        return repo.findAllByUserAndIsPaidTrue(user.get())
    }

    fun payOrder(orderId: String, isPaid: Boolean = false): Optional<Order> {
        val order = repo.findById(orderId)
        if (order.isPresent && !order.get().isPaid) {
            order.get().isPaid = isPaid
            return Optional.of(repo.save(order.get()))
        }
        return Optional.empty()
    }

    fun deleteByUser(userId: String) {
        val user = userRepository.findById(userId)
        if (!user.isPresent) return

        val orders = repo.findAllByUserAndIsPaidTrue(user.get())
        val activeOrder = repo.findByUserAndIsPaidFalse(user.get())
        if (activeOrder.isPresent) orders.add(activeOrder.get())

        for (order in orders) {
            cartItemRepository.deleteByOrder(order)
        }
        repo.deleteByUser(user.get())
    }

    fun findAll(): MutableList<Order> {
        return repo.findAll()
    }
}


@RestController
@RequestMapping("order")
class OrderController {
    @Autowired
    private lateinit var service: OrderService

    @GetMapping
    fun findAll(): MutableList<Order> {
        return service.findAll()
    }

    @PostMapping
    fun create(@RequestBody user: User): Optional<Order> {
        return service.create(user.id)
    }

    @PutMapping
    fun payOrder(@RequestBody dto: PayOrderDTO): Optional<Order> {
        return service.payOrder(dto.orderId, dto.isPaid)
    }

    @GetMapping("/{userId}")
    fun retrieveActiveByUser(@PathVariable userId: String): Optional<Order> {
        return service.retrieveActiveByUser(userId)
    }

    @GetMapping("/{userId}/history")
    fun retrievePaidByUser(@PathVariable userId: String): MutableList<Order> {
        return service.retrievePaidByUser(userId)
    }

}

data class PayOrderDTO(val orderId: String, val isPaid: Boolean)