package it.epicode.backend.capstone.utente;


import com.cloudinary.Cloudinary;
import it.epicode.backend.capstone.appuntamento.Appuntamento;
import it.epicode.backend.capstone.appuntamento.AppuntamentoRepository;
import it.epicode.backend.capstone.email.EmailService;
import it.epicode.backend.capstone.errors.InvalidLoginException;
import it.epicode.backend.capstone.professionista.Auth.LoginResponseProfessionistaDTO;
import it.epicode.backend.capstone.professionista.Auth.RegisteredProfessionistaDTO;
import it.epicode.backend.capstone.professionista.ProfessionistaRepository;
import it.epicode.backend.capstone.ruoli.Ruoli;
import it.epicode.backend.capstone.ruoli.RuoliRepository;
import it.epicode.backend.capstone.security.JwtUtils;
import it.epicode.backend.capstone.utente.Auth.*;
import it.epicode.backend.capstone.utente.appuntamentoDTO.ProfessionistaDTO;
import it.epicode.backend.capstone.utente.appuntamentoDTO.UtenteAppuntamentoDTO;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@Validated
@RequiredArgsConstructor
public class UserService {


    private final ProfessionistaRepository professionistaRepository;

    private final UserRepository userRepository;
    private final AppuntamentoRepository appuntamentoRepository;
    private final PasswordEncoder encoder;
    private final RuoliRepository rolesRepository;
    private final AuthenticationManager auth;
    private final JwtUtils jwt;
    private final EmailService emailService; // per gestire invio email di benvenuto


    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;


    // GET ALL
    public List<ResponsePrj> findAll(){
        // Questo metodo restituisce tutti gli autori presenti nel database.
        return userRepository.findAllBy();
    }


    // GET per ID
    public RegisteredUserDTO findById(Long id){
        // Questo metodo cerca un entity nel database utilizzando l'ID.
        // Se l'entity non esiste, viene generata un'eccezione.
        if(!userRepository.existsById(id)){
            throw new EntityNotFoundException("Autore non trovato");
        }
        // Se l'entity esiste, viene recuperato e le sue proprietà vengono copiate in un oggetto AutoreRegisterdUserDTO.
        User entity = userRepository.findById(id).get();
        RegisteredUserDTO RegisterdUserDTO = new RegisteredUserDTO();
        BeanUtils.copyProperties(entity, RegisterdUserDTO);
        return RegisterdUserDTO;
    }



    // PUT
    public RegisteredUserDTO modify(Long id, @Valid RegisterUserModel RegisterUserModel){
        // Questo metodo modifica un entity esistente.
        // Prima verifica se l'entity esiste nel database. Se non esiste, viene generata un'eccezione.
        if(!userRepository.existsById(id)){
            throw new EntityNotFoundException("Autore non trovato");
        }
        // Se l'entity esiste, le sue proprietà vengono modificate con quelle presenti nell'oggetto AutoreRegisterUserModel.
        User entity = userRepository.findById(id).get();
        BeanUtils.copyProperties(RegisterUserModel, entity);
        // L'entity modificato viene quindi salvato nel database e le sue proprietà vengono copiate in un oggetto AutoreRegisterdUserDTO.
        userRepository.save(entity);
        RegisteredUserDTO RegisterdUserDTO = new RegisteredUserDTO();
        BeanUtils.copyProperties(entity, RegisterdUserDTO);
        return RegisterdUserDTO;
    }

    //DELETE
    public String delete(Long id){
        // Questo metodo elimina un autore dal database.
        // Prima verifica se l'autore esiste nel database. Se non esiste, viene generata un'eccezione.
        if(!userRepository.existsById(id)){
            throw  new EntityNotFoundException("Autore non trovato");
        }
        // Se l'autore esiste, viene eliminato dal database.
        userRepository.deleteById(id);
        return "Autore eliminato";
    }
    public List<UtenteAppuntamentoDTO> getAppuntamentiByUtenteId(Long utenteId) {
        List<Appuntamento> appuntamenti = appuntamentoRepository.findByUtenteId(utenteId);
        return appuntamenti.stream().map(this::convertToUtenteAppuntamentoDTO).collect(Collectors.toList());
    }

    private UtenteAppuntamentoDTO convertToUtenteAppuntamentoDTO(Appuntamento appuntamento) {
        UtenteAppuntamentoDTO dto = new UtenteAppuntamentoDTO();
        dto.setId(appuntamento.getId());
        dto.setDataPrenotazione(appuntamento.getDataPrenotazione().toString());
        dto.setOraPrenotazione(appuntamento.getOraPrenotazione());
        dto.setStato(appuntamento.getStato());

        ProfessionistaDTO professionistaDTO = new ProfessionistaDTO();
        professionistaDTO.setId(appuntamento.getProfessionista().getId());
        professionistaDTO.setNome(appuntamento.getProfessionista().getFirstName());
        professionistaDTO.setCognome(appuntamento.getProfessionista().getLastName());
        professionistaDTO.setSpecializzazione(appuntamento.getProfessionista().getSpecializzazione());

        dto.setProfessionista(professionistaDTO);
        return dto;
    }




    public Optional<LoginResponseWrapper> login(String username, String password) {
        try {
            // Effettua il login
            log.debug("Tentativo di autenticazione per username: {}", username);
            var a = auth.authenticate(new UsernamePasswordAuthenticationToken(username, password));

            // Imposta il contesto di sicurezza
            SecurityContextHolder.getContext().setAuthentication(a);

            var user = userRepository.findOneByUsername(username).orElseThrow(() -> new NoSuchElementException("Utente non trovato"));
            log.debug("Utente trovato: {}", user);

            if (user.getRoles().contains(new Ruoli(Ruoli.ROLES_UTENTE)) || user.getRoles().contains(new Ruoli(Ruoli.ROLES_ADMIN))) {
                var dto = LoginResponseDTO.builder()
                        .withUser(RegisteredUserDTO.builder()
                                .withId(user.getId())
                                .withFirstName(user.getFirstName())
                                .withLastName(user.getLastName())
                                .withEmail(user.getEmail())
                                .withRoles(user.getRoles())
                                .withUsername(user.getUsername())
                                .withCittà(user.getCittà())
                                .build())
                        .build();
                dto.setToken(jwt.generateToken(a));
                return Optional.of(new LoginResponseWrapper(dto));
            } else if (user.getRoles().contains(new Ruoli(Ruoli.ROLES_PROFESSIONISTA))) {
                var professionista = professionistaRepository.findById(user.getId()).orElseThrow(() -> new NoSuchElementException("Professionista non trovato"));
                log.debug("Professionista trovato: {}", professionista);
                var dto = LoginResponseProfessionistaDTO.builder()
                        .withProfessionista(RegisteredProfessionistaDTO.builder()
                                .withId(user.getId())
                                .withFirstName(user.getFirstName())
                                .withLastName(user.getLastName())
                                .withEmail(user.getEmail())
                                .withCittà(user.getCittà())
                                .withRoles(user.getRoles())
                                .withUsername(user.getUsername())
                                .withSpecializzazione(professionista.getSpecializzazione())
                                .build())
                        .build();
                dto.setToken(jwt.generateToken(a));
                return Optional.of(new LoginResponseWrapper(dto));
            }
        } catch (NoSuchElementException e) {
            log.error("User not found", e);
            throw new InvalidLoginException(username, password);
        } catch (AuthenticationException e) {
            log.error("Authentication failed", e);
            throw new InvalidLoginException(username, password);
        }
        return Optional.empty();
    }

    public RegisteredUserDTO register(RegisterUserDTO register){
        if(userRepository.existsByUsername(register.getUsername())){
            throw new EntityExistsException("Utente gia' esistente");
        }
        if(userRepository.existsByEmail(register.getEmail())){
            throw new EntityExistsException("Email gia' registrata");
        }
        Ruoli roles = rolesRepository.findById(Ruoli.ROLES_UTENTE).get();
        /*
        if(!rolesRepository.existsById(Roles.ROLES_USER)){
            roles = new Roles();
            roles.setRoleType(Roles.ROLES_USER);
        } else {
            roles = rolesRepository.findById(Roles.ROLES_USER).get();
        }

         */
        User u = new User();
        BeanUtils.copyProperties(register, u);
        u.setPassword(encoder.encode(register.getPassword()));
        u.getRoles().add(roles);
        userRepository.save(u);
        RegisteredUserDTO response = new RegisteredUserDTO();
        BeanUtils.copyProperties(u, response);
        response.setRoles(List.of(roles));
        emailService.sendWelcomeEmail(u.getEmail());

        return response;

    }

    public RegisteredUserDTO registerAdmin(RegisterUserDTO register){
        if(userRepository.existsByUsername(register.getUsername())){
            throw new EntityExistsException("Utente gia' esistente");
        }
        if(userRepository.existsByEmail(register.getEmail())){
            throw new EntityExistsException("Email gia' registrata");
        }
        Ruoli roles = rolesRepository.findById(Ruoli.ROLES_ADMIN).get();
        User u = new User();
        BeanUtils.copyProperties(register, u);
        u.setPassword(encoder.encode(register.getPassword()));
        u.getRoles().add(roles);
        userRepository.save(u);
        RegisteredUserDTO response = new RegisteredUserDTO();
        BeanUtils.copyProperties(u, response);
        response.setRoles(List.of(roles));
        return response;

    }

}