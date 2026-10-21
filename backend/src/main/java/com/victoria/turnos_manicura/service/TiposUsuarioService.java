package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TiposUsuarioService {
    private final TiposUsuarioRepository repository;

    public TiposUsuarioService(TiposUsuarioRepository repository) {
        this.repository = repository;
    }


    public List<TiposUsuarioModel> listar() {
        return repository.findAll();
    }

    public TiposUsuarioModel guardar(TiposUsuarioModel tipoUsuario) {
        // Se guarda en mayúsculas, igual que CLIENTE y MANICURISTA
        tipoUsuario.setDescripcion(tipoUsuario.getDescripcion().trim().toUpperCase());

        if (repository.existsByDescripcionIgnoreCase(tipoUsuario.getDescripcion())) {
            throw new ValidacionException("descripcion", "Ya existe un tipo de usuario con esa descripción");
        }

        return repository.save(tipoUsuario);
    }
}
