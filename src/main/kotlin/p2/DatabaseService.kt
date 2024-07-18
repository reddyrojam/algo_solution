package p2

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence

object DatabaseService {
    private val entityManagerFactory: EntityManagerFactory = Persistence.createEntityManagerFactory("h2-persistence-unit")

    fun startDatabase() {
        // Initialize any resources if needed
    }

    fun stopDatabase() {
        entityManagerFactory.close()
    }

    val entityManager: EntityManager
        get() = entityManagerFactory.createEntityManager()

    fun insertInsight(insight: Insight) {
        val entityManager = entityManager
        try {
            entityManager.transaction.begin()
            if (entityManager.contains(insight)) {
                entityManager.merge(insight)
            } else {
                val managedInsight = entityManager.find(Insight::class.java, insight.objectUid)
                if (managedInsight == null) {
                    entityManager.persist(insight)
                } else {
                    entityManager.merge(insight)
                }
            }
            entityManager.transaction.commit()
        } catch (ex: Exception) {
            entityManager.transaction.rollback()
            throw ex
        } finally {
            entityManager.close()
        }
    }

    fun insertNotificationEvent(event: NotificationEvent) {
        val entityManager = entityManager
        try {
            entityManager.transaction.begin()
            if (entityManager.contains(event)) {
                entityManager.merge(event)
            } else {
                val managedEvent = entityManager.find(NotificationEvent::class.java, event.id)
                if (managedEvent == null) {
                    entityManager.persist(event)
                } else {
                    entityManager.merge(event)
                }
            }
            entityManager.transaction.commit()
        } catch (ex: Exception) {
            entityManager.transaction.rollback()
            throw ex
        } finally {
            entityManager.close()
        }
    }

    fun fetchInsights(): List<Insight> {
        val entityManager = entityManager
        return try {
            val query = entityManager.createQuery("SELECT i FROM Insight i", Insight::class.java)
            query.resultList
        } finally {
            entityManager.close()
        }
    }
}