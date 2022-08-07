package kz.shymbulak.mirastest

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.util.*

@Document(collection = "product")
data class Product(
    @JsonProperty var type: String,
    @JsonProperty var price: Int,
) {
    @Id
    @JsonProperty
    lateinit var id: String
}


@Repository
interface ProductsRepository : MongoRepository<Product, String> {
    override fun findAll(): MutableList<Product>
    override fun deleteById(id: String)
    override fun findById(id: String): Optional<Product>
    override fun <S : Product> insert(entity: S): S
    override fun <S : Product> save(entity: S): S
}

@Service
class ProductsService {
    @Autowired
    private lateinit var repo: ProductsRepository

    @Autowired
    private lateinit var cartItemssRepository: CartItemRepository

    fun create(type: String, price: Int): Product {
        return repo.insert(Product(type, price))
    }

    fun retrieve(id: String): Optional<Product> {
        return repo.findById(id)
    }

    fun update(id: String, type: String, price: Int): Optional<Product> {
        val user = repo.findById(id)
        if (user.isPresent) {
            user.get().type = type
            user.get().price = price
            return Optional.of(repo.save(user.get()))
        }
        return Optional.empty()
    }

    fun delete(id: String) {
        val product = this.retrieve(id)
        if (!product.isPresent) return

        cartItemssRepository.deleteByProduct(product.get())
        repo.deleteById(id)
    }

    fun findAll(): MutableList<Product> {
        return repo.findAll()
    }
}

@RestController
@RequestMapping("product")
class ProductController {
    @Autowired
    private lateinit var service: ProductsService

    @GetMapping
    fun findAll(): MutableList<Product> {
        return service.findAll()
    }

    @PostMapping
    fun create(@RequestBody product: Product): Product {
        return service.create(product.type, product.price)
    }

    @PutMapping
    fun update(@RequestBody product: Product): Optional<Product> {
        return service.update(product.id, product.type, product.price)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String) {
        service.delete(id)
    }

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: String): Optional<Product> {
        return service.retrieve(id)
    }

}