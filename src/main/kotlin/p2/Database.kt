package p2

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence

object Database {
    private val entityManagerFactory: EntityManagerFactory = Persistence.createEntityManagerFactory("h2-persistence-unit")

    val entityManager: EntityManager
        get() = entityManagerFactory.createEntityManager()
}
