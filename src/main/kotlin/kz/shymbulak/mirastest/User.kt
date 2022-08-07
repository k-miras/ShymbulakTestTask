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

@Document(collection = "user")
data class User(

    @JsonProperty
    var name: String,
) {
    @Id
    @JsonProperty
    lateinit var id: String
}

@Repository
interface UserRepository : MongoRepository<User, String> {
    override fun findAll(): MutableList<User>
    override fun deleteById(id: String)
    override fun findById(id: String): Optional<User>
    override fun <S : User> insert(entity: S): S
    override fun <S : User> save(entity: S): S
    override fun existsById(id: String): Boolean
}

@Service
class UserService {
    @Autowired
    private lateinit var repo: UserRepository

    @Autowired
    private lateinit var orderService: OrderService
    fun create(name: String): User {
        val user = repo.insert(User(name))
        orderService.create(user.id)
        return user
    }

    fun retrieve(id: String): Optional<User> {
        return repo.findById(id)
    }

    fun update(id: String, name: String): Optional<User> {
        val user = repo.findById(id)
        if (user.isPresent) {
            user.get().name = name
            return Optional.of(repo.save(user.get()))
        }
        return Optional.empty()
    }

    fun delete(id: String) {
        orderService.deleteByUser(id)
        repo.deleteById(id)
    }

    fun findAll(): MutableList<User> {
        return repo.findAll()
    }
}


@RestController
@RequestMapping("user")
class UserController {
    @Autowired
    private lateinit var service: UserService

    @GetMapping
    fun findAll(): MutableList<User> {
        return service.findAll()
    }

    @PostMapping
    fun create(@RequestBody user: User): User {
        return service.create(user.name)
    }

    @PutMapping
    fun update(@RequestBody user: User): Optional<User> {
        return service.update(user.id, user.name)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String) {
        service.delete(id)
    }

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: String): Optional<User> {
        return service.retrieve(id)
    }

}

