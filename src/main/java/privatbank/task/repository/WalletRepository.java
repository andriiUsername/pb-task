package privatbank.task.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import privatbank.task.model.WalletEntity;

public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.id = :id")
    Optional<WalletEntity> findByIdForUpdate(long id);

    @Query("select w.ownerId from WalletEntity w where w.id = :id")
    Optional<String> findOwnerIdById(long id);
}
