package marc.dev.secure_document_system.repository;


import marc.dev.secure_document_system.entity.ConfirmationEntity;
import marc.dev.secure_document_system.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;



@Repository
public interface ConfirmationRepository extends JpaRepository<ConfirmationEntity, Long> {
    Optional<ConfirmationEntity> findByToken(String token);
    Optional<ConfirmationEntity> findByUserEntity(UserEntity userEntity);
}