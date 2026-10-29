// sesion: { token, id, nombre, apellido, email, rol } — rol es CLIENTE o MANICURISTA
// turnos = todos los turnos del usuario (solo cliente y manicurista: alimentan "mis turnos" y la detección de superposiciones);
// pagina = la página de la tabla de turnos tal como la devuelve el backend (el admin solo trabaja con esta)
const TURNOS_POR_PAGINA = 20;
const state = { tipos: [], usuarios: [], servicios: [], ofertas: [], turnos: [], pagina: { content: [], number: 0, totalPages: 0, totalElements: 0 }, sesion: null };
// Qué se está editando en los formularios del admin (null = alta nueva)
let usuarioEnEdicion = null;
let servicioEnEdicion = null;

const $ = (sel) => document.querySelector(sel);
const apiInput = $('#apiUrl');

// Recordar la URL del backend entre recargas
try {
  const saved = localStorage.getItem('apiUrl');
  if (saved) apiInput.value = saved;
} catch {}
apiInput.addEventListener('change', () => {
  try { localStorage.setItem('apiUrl', apiInput.value.trim()); } catch {}
  if (state.sesion) cargarTodo();
});

// ---------- API ----------

async function api(path, options = {}) {
  const base = apiInput.value.trim().replace(/\/$/, '');
  const res = await fetch(base + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(state.sesion ? { Authorization: `Bearer ${state.sesion.token}` } : {}),
    },
  });
  if (res.status === 401 && state.sesion) {
    // El backend se reinició o la sesión venció
    cerrarSesion();
    toast('Tu sesión venció, volvé a iniciar sesión', true);
  }
  if (!res.ok) {
    const error = new Error(`Error ${res.status}`);
    try {
      const body = await res.json();
      if (body.message) error.message = body.message;
      error.errores = body.errores; // { campo: mensaje } del ErrorHandler
    } catch {}
    throw error;
  }
  const texto = await res.text();
  return texto ? JSON.parse(texto) : null;
}

const get = (path) => api(path);
const post = (path, data) => api(path, { method: 'POST', body: JSON.stringify(data) });

// ---------- Utilidades ----------

function toast(msg, isError = false) {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.toggle('error', isError);
  el.classList.add('show');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => el.classList.remove('show'), 3500);
}

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

const nombreCompleto = (u) => u ? `${u.nombre ?? ''} ${u.apellido ?? ''}`.trim() : '—';
const usuarioPorId = (id) => state.usuarios.find((u) => u.id === id);
const servicioPorId = (id) => state.servicios.find((s) => s.id === id);
const esManicurista = () => state.sesion?.rol === 'MANICURISTA';
const esCliente = () => state.sesion?.rol === 'CLIENTE';
const esPersonal = () => esManicurista() || state.sesion?.rol === 'ADMIN';

const pad2 = (n) => String(n).padStart(2, '0');
const fechaDeBackend = (diaHorario) => new Date(String(diaHorario).replace(' ', 'T'));
const hhmm = (date) => `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
const minutosAHHMM = (min) => `${pad2(Math.floor(min / 60))}:${pad2(min % 60)}`;
const hoy = () => new Date().toLocaleDateString('en-CA');

// Horario de atención (igual que en el backend)
const APERTURA_MIN = 6 * 60;
const CIERRE_MIN = 23 * 60;

// Fin de un turno a partir de su duración en minutos
const finDeTurno = (inicio, minutos) => new Date(inicio.getTime() + (minutos || 0) * 60000);

// Duración con la que se reservó el turno (los viejos, sin dato, usan la del catálogo)
function minutosDeTurno(t) {
  if (t.duracion) return horaAMinutos(t.duracion);
  const servicio = servicioPorId(t.idServicio);
  return servicio?.duracion ? horaAMinutos(servicio.duracion) : 0;
}

// Cuánto tarda esa manicurista en ese servicio (si no hay dato, lo del catálogo)
function minutosDeOferta(idManicurista, idServicio) {
  const oferta = state.ofertas.find((o) => o.idManicurista === idManicurista && o.idServicio === idServicio);
  const duracion = oferta?.duracion ?? servicioPorId(idServicio)?.duracion;
  return duracion ? horaAMinutos(duracion) : 0;
}
const tipoPorId = (id) => state.tipos.find((t) => t.id === id);

// Busca el ID del tipo por su descripción (CLIENTE / MANICURISTA) en vez de asumir el número
const idTipo = (descripcion) =>
  state.tipos.find((t) => String(t.descripcion).toUpperCase() === descripcion)?.id;
const usuariosDeTipo = (descripcion) => {
  const id = idTipo(descripcion);
  return id == null ? [] : state.usuarios.filter((u) => u.tipo === id && u.activo !== false);
};

// La duración viaja como "HH:mm:ss" (columna TIME); en pantalla se usa en minutos
function horaAMinutos(hhmmss) {
  if (!hhmmss) return null;
  const [h, m] = String(hhmmss).split(':').map(Number);
  return h * 60 + m;
}
function minutosAHora(min) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${pad(Math.floor(min / 60))}:${pad(min % 60)}:00`;
}

function formatearFecha(diaHorario) {
  // El backend usa el formato "yyyy-MM-dd HH:mm:ss"
  const d = new Date(String(diaHorario).replace(' ', 'T'));
  if (isNaN(d)) return esc(diaHorario);
  return d.toLocaleString('es-AR', {
    weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function filaVacia(tbody, cols, texto) {
  tbody.innerHTML = `<tr><td class="empty" colspan="${cols}">${texto}</td></tr>`;
}

function opciones(select, items, label, placeholder) {
  select.innerHTML = (placeholder ? `<option value="">${placeholder}</option>` : '') +
    items.map((i) => `<option value="${i.id}">${esc(label(i))}</option>`).join('');
}

// ---------- Validación ----------

// Campos del backend que en el formulario tienen otro nombre
const ALIAS_CAMPOS = { diaHorario: 'fecha' };

function limpiarErrores(form) {
  form.querySelectorAll('.field-error').forEach((el) => el.remove());
  form.querySelectorAll('.invalid').forEach((el) => el.classList.remove('invalid'));
}

function mostrarErrores(form, errores) {
  let primero = null;
  for (const [campo, mensaje] of Object.entries(errores)) {
    const input = form.elements[ALIAS_CAMPOS[campo] ?? campo];
    if (!input || !input.classList) continue;
    input.classList.add('invalid');
    const span = document.createElement('span');
    span.className = 'field-error';
    span.textContent = mensaje;
    input.closest('label').append(span);
    primero ??= input;
  }
  primero?.focus();
}

// Al corregir un campo se borra su error
document.querySelectorAll('form').forEach((form) => {
  form.addEventListener('input', (e) => {
    const label = e.target.closest('label');
    e.target.classList.remove('invalid');
    label?.querySelector('.field-error')?.remove();
  });
});

const LETRAS = /^[\p{L} '.-]+$/u;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// Teléfono opcional: solo dígitos con código de área (10 a 15). Es lo que necesita WhatsApp
const errorTelefono = (telefono) => telefono && !/^\d{10,15}$/.test(telefono)
  ? 'El teléfono tiene que tener entre 10 y 15 dígitos, con código de área y sin espacios ni guiones'
  : '';
const mismoTexto = (a, b) => String(a ?? '').trim().toLowerCase() === String(b ?? '').trim().toLowerCase();

// Los clientes reservan siempre para sí mismos
const clienteDelTurno = (f) => esCliente() ? state.sesion.id : Number(f.idCliente.value);

// Un turno se pisa con otro si empieza antes de que el otro termine (y termina después de que el otro empiece)
function seSuperpone(turnos, inicio, fin) {
  return turnos.some((t) => {
    const inicioT = fechaDeBackend(t.diaHorario);
    const finT = finDeTurno(inicioT, minutosDeTurno(t));
    return inicio < finT && fin > inicioT;
  });
}

function validarTurno(f) {
  const e = {};
  if (!f.idManicurista.value) e.idManicurista = 'Elegí una manicurista';
  if (!esCliente() && !f.idCliente.value) e.idCliente = 'Elegí un cliente';
  if (!f.idServicio.value) e.idServicio = 'Elegí un servicio';
  if (!f.fecha.value) e.fecha = 'Elegí una fecha';
  if (!f.hora.value) e.hora = 'Elegí una hora';
  if (e.fecha || e.hora || e.idServicio) return e;

  const inicio = new Date(`${f.fecha.value}T${f.hora.value}`);
  const limite = new Date();
  limite.setMonth(limite.getMonth() + 6);
  if (inicio < new Date()) return { ...e, fecha: 'No se pueden reservar turnos en el pasado' };
  if (inicio > limite) return { ...e, fecha: 'Solo se pueden reservar turnos hasta 6 meses adelante' };

  const fin = finDeTurno(inicio, minutosDeOferta(Number(f.idManicurista.value), Number(f.idServicio.value)));
  const minInicio = inicio.getHours() * 60 + inicio.getMinutes();
  const mismoDia = fin.toDateString() === inicio.toDateString();
  if (minInicio < APERTURA_MIN || !mismoDia || fin.getHours() * 60 + fin.getMinutes() > CIERRE_MIN) {
    return { ...e, hora: `El turno tiene que estar entre las ${minutosAHHMM(APERTURA_MIN)} y las ${minutosAHHMM(CIERRE_MIN)} hs (este servicio termina a las ${hhmm(fin)})` };
  }

  const activos = state.turnos.filter((t) => t.activo);
  const idManicurista = Number(f.idManicurista.value);
  if (seSuperpone(activos.filter((t) => t.idManicurista === idManicurista), inicio, fin)) {
    e.hora = 'La manicurista ya tiene un turno que se superpone con ese horario';
  } else if (seSuperpone(activos.filter((t) => t.idCliente === clienteDelTurno(f)), inicio, fin)) {
    e.hora = 'El cliente ya tiene otro turno que se superpone con ese horario';
  }
  return e;
}

function validarServicio(f) {
  const e = {};
  const nombre = f.nombre.value.trim();
  if (!nombre) e.nombre = 'El nombre es obligatorio';
  else if (state.servicios.some((s) => s.id !== servicioEnEdicion && mismoTexto(s.nombre, nombre))) e.nombre = 'Ya existe un servicio con ese nombre';

  const duracion = Number(f.duracion.value);
  if (!f.duracion.value) e.duracion = 'La duración es obligatoria';
  else if (!Number.isInteger(duracion) || duracion < 5) e.duracion = 'La duración mínima es de 5 minutos';
  else if (duracion > 480) e.duracion = 'La duración máxima es de 8 horas (480 min)';

  const precio = Number(f.precio.value);
  if (!f.precio.value) e.precio = 'El precio es obligatorio';
  else if (!Number.isInteger(precio) || precio <= 0) e.precio = 'El precio tiene que ser un número entero mayor a 0';
  else if (precio > 99999999) e.precio = 'El precio es demasiado alto';
  return e;
}

function validarUsuario(f) {
  const e = {};
  if (!f.tipo.value) e.tipo = 'Elegí un tipo de usuario';

  for (const campo of ['nombre', 'apellido']) {
    const valor = f[campo].value.trim();
    const etiqueta = campo === 'nombre' ? 'El nombre' : 'El apellido';
    if (!valor) e[campo] = `${etiqueta} es obligatorio`;
    else if (!LETRAS.test(valor)) e[campo] = `${etiqueta} solo puede tener letras`;
  }

  const telefono = f.numeroContacto.value.trim();
  if (errorTelefono(telefono)) e.numeroContacto = errorTelefono(telefono);

  const email = f.email.value.trim();
  if (email &&!EMAIL.test(email)) e.email = 'El email no tiene un formato válido';
  else if (email && state.usuarios.some((u) => u.id !== usuarioEnEdicion && mismoTexto(u.email, email))) e.email = 'Ya hay un usuario registrado con ese email';
  else if (!email) e.email = 'El email es obligatorio para poder iniciar sesión';

  // Al editar, la contraseña solo se valida si se escribe una nueva
  if ((usuarioEnEdicion == null || f.password.value) && f.password.value.length < 6) e.password = 'La contraseña tiene que tener al menos 6 caracteres';
  return e;
}

function validarTipo(f) {
  const descripcion = f.descripcion.value.trim();
  if (!descripcion) return { descripcion: 'La descripción es obligatoria' };
  if (state.tipos.some((t) => mismoTexto(t.descripcion, descripcion))) {
    return { descripcion: 'Ya existe un tipo de usuario con esa descripción' };
  }
  return {};
}

async function enviarForm(form, validar, accion) {
  limpiarErrores(form);
  const errores = validar(form);
  if (Object.keys(errores).length) {
    mostrarErrores(form, errores);
    return;
  }

  const btn = form.querySelector('button[type="submit"]');
  btn.disabled = true;
  try {
    await accion();
    form.reset();
  } catch (e) {
    if (e.errores && Object.keys(e.errores).length) mostrarErrores(form, e.errores);
    toast(e.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : e.message, true);
  } finally {
    btn.disabled = false;
  }
}

// ---------- Render ----------

function renderTipos() {
  const tbody = $('#tablaTipos');
  if (!state.tipos.length) return filaVacia(tbody, 2, 'No hay tipos de usuario');
  tbody.innerHTML = state.tipos
    .map((t) => `<tr><td>${t.id}</td><td>${esc(t.descripcion)}</td></tr>`)
    .join('');

  opciones($('#formUsuario [name="tipo"]'), state.tipos, (t) => t.descripcion);
}

// Una manicurista solo carga turnos propios: el campo queda fijo en ella
function fijarManicurista() {
  if (esManicurista()) $('#formTurno [name="idManicurista"]').value = String(state.sesion.id);
  renderServiciosTurno();
}
$('#formTurno').addEventListener('reset', () => setTimeout(fijarManicurista));

function renderUsuarios() {
  const tbody = $('#tablaUsuarios');
  if (!state.usuarios.length) {
    filaVacia(tbody, 7, 'No hay usuarios');
  } else {
    tbody.innerHTML = state.usuarios.map((u) => `
      <tr class="${u.activo === false ? 'fila-baja' : ''}">
        <td>${u.id}</td>
        <td>${esc(nombreCompleto(u))}</td>
        <td>${esc(tipoPorId(u.tipo)?.descripcion ?? u.tipo)}</td>
        <td>${esc(u.numeroContacto || '')}</td>
        <td>${esc(u.email)}</td>
        <td><span class="badge ${u.activo === false ? 'off' : 'on'}">${u.activo === false ? 'De baja' : 'Activo'}</span></td>
        <td class="acciones">
          <button class="btn-link editar" data-editar-usuario="${u.id}">Editar</button>
          ${u.id === state.sesion?.id ? '' : (u.activo === false
            ? `<button class="btn-link editar" data-alta-usuario="${u.id}">Reactivar</button>`
            : `<button class="btn-link" data-baja-usuario="${u.id}">Dar de baja</button>`)}
        </td>
      </tr>`).join('');
  }

  const manicuristas = usuariosDeTipo('MANICURISTA');
  const clientes = usuariosDeTipo('CLIENTE');
  opciones($('#formTurno [name="idManicurista"]'), manicuristas, nombreCompleto, 'Elegí una manicurista');
  fijarManicurista();
  opciones($('#formTurno [name="idCliente"]'), clientes, nombreCompleto, 'Elegí un cliente');

  const filtro = $('#filtroManicurista');
  const actual = filtro.value;
  opciones(filtro, manicuristas, nombreCompleto, 'Todas');
  filtro.value = actual;

  // La agenda arranca con la manicurista que inició sesión
  const agenda = $('#agendaManicurista');
  const agendaActual = agenda.value || (esManicurista() ? String(state.sesion.id) : '');
  opciones(agenda, manicuristas, nombreCompleto);
  agenda.value = agendaActual;
}

const dinero = (n) => `$${Number(n ?? 0).toLocaleString('es-AR')}`;
const ofertaDe = (idManicurista, idServicio) =>
  state.ofertas.find((o) => o.idManicurista === idManicurista && o.idServicio === idServicio);

// Cada manicurista ofrece servicios del catálogo con su propio precio
function renderServicios() {
  renderServiciosTurno();
  renderMisServicios();

  // Catálogo (clientes y admin): el cliente ve desde qué precio y cuántas manicuristas lo ofrecen
  const cont = $('#listaServicios');
  if (!state.servicios.length) {
    cont.innerHTML = '<p class="empty">No hay servicios cargados</p>';
    return;
  }
  const admin = state.sesion?.rol === 'ADMIN';
  cont.innerHTML = state.servicios.filter((s) => admin || s.activo).map((s) => {
    const activas = state.ofertas.filter((o) => o.idServicio === s.id && o.activo);
    const desde = activas.length ? Math.min(...activas.map((o) => o.precio)) : null;
    const quienes = activas.length === 1 ? '1 manicurista' : `${activas.length} manicuristas`;
    return `
    <div class="servicio ${s.activo ? '' : 'inactivo'}">
      <h3>${esc(s.nombre)}</h3>
      <p>${esc(s.descripcion)}${s.duracion ? ` · ${horaAMinutos(s.duracion)} min` : ''}</p>
      <div class="meta">
        <span class="precio">${admin
          ? `Sugerido ${dinero(s.precio)}`
          : (desde == null ? 'Sin precio' : `Desde ${dinero(desde)}`)}</span>
        <span class="badge ${s.activo && activas.length ? 'on' : 'off'}">${!s.activo ? 'De baja' : (activas.length ? quienes : 'Sin manicuristas')}</span>
      </div>
      ${admin ? `
      <div class="bloqueo-acciones">
        <button class="btn-link editar" data-editar-servicio="${s.id}">Editar</button>
        ${s.activo
          ? `<button class="btn-link" data-baja-servicio="${s.id}">Dar de baja</button>`
          : `<button class="btn-link editar" data-alta-servicio="${s.id}">Reactivar</button>`}
      </div>` : ''}
    </div>`;
  }).join('');
}

// Solo las manicuristas: sus servicios (con precio propio, baja y reactivación) y lo que les falta agregar del catálogo
function renderMisServicios() {
  if (!esManicurista()) return;
  const mias = state.ofertas.filter((o) => o.idManicurista === state.sesion.id)
    .map((o) => ({ ...o, servicio: servicioPorId(o.idServicio) }))
    .filter((o) => o.servicio)
    .sort((a, b) => a.servicio.nombre.localeCompare(b.servicio.nombre));

  $('#misServicios').innerHTML = mias.length ? mias.map((o) => `
    <div class="servicio ${o.activo ? '' : 'inactivo'}">
      <h3>${esc(o.servicio.nombre)}</h3>
      <p>${esc(o.servicio.descripcion)} · catálogo: ${horaAMinutos(o.servicio.duracion)} min</p>
      <div class="precio-editor">
        <label>Mi precio ($)
          <input type="number" min="1" max="99999999" value="${o.precio}" data-precio="${o.id}">
        </label>
        <label>Mi duración (min)
          <input type="number" min="5" max="480" value="${horaAMinutos(o.duracion)}" data-duracion="${o.id}">
        </label>
        <button class="btn-link editar" data-guardar-precio="${o.id}">Guardar</button>
      </div>
      <div class="meta">
        <span class="badge ${o.activo ? 'on' : 'off'}">${o.activo ? 'Activo' : 'De baja'}</span>
        ${o.activo
          ? `<button class="btn-link" data-baja="${o.id}">Dar de baja</button>`
          : `<button class="btn-link editar" data-alta="${o.id}">Reactivar</button>`}
      </div>
    </div>`).join('') : '<p class="empty">Todavía no ofrecés ningún servicio: agregalos desde el catálogo de abajo</p>';

  const faltantes = state.servicios.filter((s) => s.activo && !ofertaDe(state.sesion.id, s.id));
  $('#btnAgregarTodos').hidden = !faltantes.length;
  $('#btnAgregarTodos').textContent = `Agregar todos (${faltantes.length})`;
  $('#catalogoParaAgregar').innerHTML = faltantes.length ? faltantes.map((s) => `
    <div class="servicio">
      <h3>${esc(s.nombre)}</h3>
      <p>${esc(s.descripcion)} · ${horaAMinutos(s.duracion)} min</p>
      <div class="meta">
        <span class="precio">${dinero(s.precio)}</span>
        <button class="btn" data-agregar="${s.id}">+ Agregar</button>
      </div>
    </div>`).join('') : '<p class="empty">Ya ofrecés todos los servicios del catálogo</p>';
}

// El cliente elige primero la manicurista: el servicio muestra solo lo que ella ofrece y a su precio
function renderServiciosTurno() {
  const f = $('#formTurno');
  const select = f.idServicio;
  const elegido = select.value;
  const idManicurista = esManicurista() ? state.sesion.id : Number(f.idManicurista.value);

  if (!idManicurista) {
    opciones(select, [], () => '', 'Elegí primero una manicurista');
    select.disabled = true;
    return;
  }

  const propios = state.ofertas
    .filter((o) => o.idManicurista === idManicurista && o.activo)
    .map((o) => ({ oferta: o, servicio: servicioPorId(o.idServicio) }))
    .filter((x) => x.servicio && x.servicio.activo)
    .sort((a, b) => a.servicio.nombre.localeCompare(b.servicio.nombre));

  select.disabled = false;
  opciones(select, propios.map((x) => x.servicio),
    (s) => `${s.nombre} · ${dinero(ofertaDe(idManicurista, s.id).precio)} (${minutosDeOferta(idManicurista, s.id)} min)`,
    propios.length ? 'Elegí un servicio' : 'Esta manicurista no ofrece servicios');
  select.value = propios.some((x) => x.servicio.id === Number(elegido)) ? elegido : '';
}

async function recargarOfertas() {
  await cargar('ofertas', '/servicios/ofertas', renderServicios);
}

async function cambiarOferta(id, cambios, mensaje) {
  try {
    await api(`/servicios/ofertas/${id}`, { method: 'PUT', body: JSON.stringify(cambios) });
    toast(mensaje);
    await recargarOfertas();
  } catch (err) {
    toast(err.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : err.message, true);
  }
}

async function agregarOfertas(idsServicio) {
  try {
    for (const idServicio of idsServicio) await post('/servicios/ofertas', { idServicio });
    toast(idsServicio.length === 1 ? 'Servicio agregado' : `${idsServicio.length} servicios agregados`);
    await recargarOfertas();
  } catch (err) {
    toast(err.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : err.message, true);
    await recargarOfertas();
  }
}

$('#misServicios').addEventListener('click', (e) => {
  const d = e.target.dataset;
  if (d.guardarPrecio) {
    const precio = Number($(`[data-precio="${d.guardarPrecio}"]`).value);
    const minutos = Number($(`[data-duracion="${d.guardarPrecio}"]`).value);
    if (!Number.isInteger(precio) || precio <= 0 || precio > 99999999) {
      return toast('El precio tiene que ser un número entero mayor a 0', true);
    }
    if (!Number.isInteger(minutos) || minutos < 5 || minutos > 480) {
      return toast('La duración tiene que ser de entre 5 y 480 minutos', true);
    }
    cambiarOferta(d.guardarPrecio, { precio, duracion: minutosAHora(minutos) }, 'Servicio actualizado');
  }
  if (d.baja && confirm('¿Dar de baja este servicio? Los clientes dejarán de poder reservarlo; los turnos ya reservados se mantienen.')) {
    cambiarOferta(d.baja, { activo: false }, 'Servicio dado de baja');
  }
  if (d.alta) cambiarOferta(d.alta, { activo: true }, 'Servicio reactivado');
});

$('#catalogoParaAgregar').addEventListener('click', (e) => {
  if (e.target.dataset.agregar) agregarOfertas([Number(e.target.dataset.agregar)]);
});

$('#btnAgregarTodos').addEventListener('click', () => {
  agregarOfertas(state.servicios.filter((s) => s.activo && !ofertaDe(state.sesion.id, s.id)).map((s) => s.id));
});

// Al cambiar de manicurista se actualiza la lista de servicios del turno
$('#formTurno').idManicurista.addEventListener('change', renderServiciosTurno);

// ---------- Mis turnos (clientes) ----------

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const DIAS_SEMANA = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
const claveDia = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;

function renderMisTurnos() {
  if (state.sesion?.rol !== 'CLIENTE') return;

  const ahora = new Date();
  const proximos = state.turnos
    .filter((t) => t.activo && t.idCliente === state.sesion.id)
    .map((t) => ({ ...t, inicio: fechaDeBackend(t.diaHorario) }))
    .filter((t) => t.inicio >= ahora)
    .sort((a, b) => a.inicio - b.inicio);

  // Calendario: mes actual y el siguiente, con color en los días que tienen turno
  const porDia = {};
  for (const t of proximos) (porDia[claveDia(t.inicio)] ??= []).push(t);

  const hoyClave = claveDia(ahora);
  let html = '';
  for (let m = 0; m < 2; m++) {
    const primero = new Date(ahora.getFullYear(), ahora.getMonth() + m, 1);
    const diasDelMes = new Date(primero.getFullYear(), primero.getMonth() + 1, 0).getDate();
    const hueco = (primero.getDay() + 6) % 7; // la semana empieza en lunes

    let celdas = '<span></span>'.repeat(hueco);
    for (let dia = 1; dia <= diasDelMes; dia++) {
      const fecha = new Date(primero.getFullYear(), primero.getMonth(), dia);
      const clave = claveDia(fecha);
      const turnos = porDia[clave] ?? [];
      const clases = ['cal-dia', turnos.length ? 'reservado' : '', clave === hoyClave ? 'hoy' : '', clave < hoyClave ? 'pasado' : '']
        .filter(Boolean).join(' ');
      const detalle = turnos.map((t) => `${hhmm(t.inicio)} ${servicioPorId(t.idServicio)?.nombre ?? ''}`).join('\n');
      celdas += `<span class="${clases}"${detalle ? ` title="${esc(detalle)}"` : ''}>${dia}</span>`;
    }

    html += `
      <div class="cal-mes">
        <h3>${MESES[primero.getMonth()]} ${primero.getFullYear()}</h3>
        <div class="cal-grid">
          ${DIAS_SEMANA.map((d) => `<b>${d}</b>`).join('')}
          ${celdas}
        </div>
      </div>`;
  }
  $('#calendario').innerHTML = html;

  // Tarjetas con los datos de cada turno
  const cont = $('#tarjetasTurnos');
  if (!proximos.length) {
    cont.innerHTML = '<p class="empty">No tenés turnos reservados</p>';
    return;
  }
  cont.innerHTML = proximos.map((t) => {
    const servicio = servicioPorId(t.idServicio);
    const manicurista = usuarioPorId(t.idManicurista);
    const fin = finDeTurno(t.inicio, minutosDeTurno(t));
    const fecha = t.inicio.toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' });
    return `
      <article class="turno-card">
        <div class="turno-fecha">${esc(fecha)}</div>
        <div class="turno-hora">${hhmm(t.inicio)} – ${hhmm(fin)} hs</div>
        <dl>
          <dt>Servicio</dt><dd>${esc(servicio?.nombre ?? '—')}${servicio ? ` · ${dinero(t.precio ?? servicio.precio)}` : ''}</dd>
          <dt>Manicurista</dt><dd>${esc(nombreCompleto(manicurista))}</dd>
          <dt>Dirección</dt><dd>${esc(manicurista?.direccion || 'A confirmar con la manicurista')}</dd>
          ${manicurista?.numeroContacto ? `<dt>Contacto</dt><dd>${esc(manicurista.numeroContacto)}</dd>` : ''}
        </dl>
        <div class="bloqueo-acciones">
          <button class="btn-link editar" data-reprogramar="${t.id}">Reprogramar</button>
          <button class="btn-link" data-cancelar="${t.id}">Cancelar turno</button>
        </div>
      </article>`;
  }).join('');
}

// Estado que calcula el backend: PROXIMO (activo y por venir), REALIZADO (activo y ya pasó),
// NO_ASISTIO (el cliente no se presentó) o CANCELADO
const ESTADOS_TURNO = {
  PROXIMO: { texto: 'Próximo', clase: 'on' },
  REALIZADO: { texto: 'Realizado', clase: 'done' },
  NO_ASISTIO: { texto: 'No asistió', clase: 'warn' },
  CANCELADO: { texto: 'Cancelado', clase: 'off' },
};

// Enlace de WhatsApp (wa.me, gratis: abre el chat con el mensaje escrito y la persona lo envía).
// Con 10 dígitos se asume un número argentino y se le antepone 549 (país + celular)
function enlaceWhatsApp(telefono, texto) {
  const numero = String(telefono ?? '').replace(/\D/g, '');
  if (numero.length < 10) return null;
  const completo = numero.length === 10 ? `549${numero}` : numero;
  return `https://wa.me/${completo}?text=${encodeURIComponent(texto)}`;
}

// Mensajes que la manicurista le manda al cliente: confirmar la reserva, recordarla o avisar la cancelación
function mensajeWhatsApp(tipo, t) {
  const cliente = usuarioPorId(t.idCliente);
  const manicurista = usuarioPorId(t.idManicurista);
  const servicio = servicioPorId(t.idServicio)?.nombre ?? 'turno';
  const inicio = fechaDeBackend(t.diaHorario);
  const cuando = `${inicio.toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' })} a las ${hhmm(inicio)} hs`;
  const direccion = manicurista?.direccion ? ` Te espero en ${manicurista.direccion}.` : '';
  const saludo = `Hola ${cliente?.nombre ?? ''}!`;
  if (tipo === 'cancelacion') {
    return `${saludo} Te aviso que se canceló tu turno de ${servicio} del ${cuando}. Si querés, coordinamos otro horario.`;
  }
  if (tipo === 'recordatorio') {
    return `${saludo} Te recuerdo que tenés turno de ${servicio} el ${cuando}.${direccion} ¡Te espero!`;
  }
  return `${saludo} Quedó reservado tu turno de ${servicio} el ${cuando}.${direccion} ¡Gracias!`;
}

// Logo de WhatsApp (Simple Icons, 24x24). Va en el HTML tal cual y toma el color del texto (currentColor)
const ICONO_WHATSAPP = '<svg class="icono-whatsapp" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/></svg>';

// Links para avisar por WhatsApp al cliente del turno (solo manicuristas y admin, y si el cliente tiene teléfono)
function avisosWhatsApp(t) {
  if (!esPersonal()) return '';
  const telefono = usuarioPorId(t.idCliente)?.numeroContacto;
  const tipos = t.estado === 'PROXIMO'
    ? [['reserva', 'Confirmar'], ['recordatorio', 'Recordar']]
    : t.estado === 'CANCELADO' ? [['cancelacion', 'Avisar cancelación']] : [];
  return tipos.map(([tipo, texto]) => {
    const url = enlaceWhatsApp(telefono, mensajeWhatsApp(tipo, t));
    return url ? `<a class="btn-link whatsapp" href="${esc(url)}" target="_blank" rel="noopener" title="Abrir WhatsApp con el mensaje armado">${ICONO_WHATSAPP}${texto}</a>` : '';
  }).join('');
}

// Botones de la columna de acciones según el estado. Marcar la asistencia es solo para manicuristas y admin
function accionesDeTurno(t) {
  const whatsapp = avisosWhatsApp(t);
  if (t.estado === 'PROXIMO') {
    return `<button class="btn-link editar" data-reprogramar="${t.id}">Reprogramar</button><button class="btn-link" data-cancelar="${t.id}">Cancelar</button>${whatsapp}`;
  }
  if (!esPersonal()) return '';
  if (t.estado === 'CANCELADO') return whatsapp;
  if (t.estado === 'REALIZADO') {
    return `<button class="btn-link" data-asistencia="${t.id}" data-no-asistio="true">Marcar no asistió</button>`;
  }
  if (t.estado === 'NO_ASISTIO') {
    return `<button class="btn-link editar" data-asistencia="${t.id}" data-no-asistio="false">Corregir: sí asistió</button>`;
  }
  return '';
}

// Pide la página de la tabla (ya filtrada y ordenada por el backend). El admin no baja la lista completa
async function cargarTurnos(pagina = state.pagina.number) {
  const filtro = Number($('#filtroManicurista').value) || null;
  const momento = $('#filtroMomento').value;
  const query = `page=${pagina}&size=${TURNOS_POR_PAGINA}&momento=${momento}${filtro ? `&idManicurista=${filtro}` : ''}`;
  const pedidos = [get(`/turnos/pagina?${query}`)];
  if (state.sesion?.rol !== 'ADMIN') pedidos.push(get('/turnos'));
  const [paginaNueva, todos] = await Promise.all(pedidos);

  // Si se canceló o se filtró y la página ya no existe, se vuelve a pedir la última
  if (paginaNueva.totalPages && pagina >= paginaNueva.totalPages) return cargarTurnos(paginaNueva.totalPages - 1);
  state.pagina = paginaNueva;
  if (todos) state.turnos = todos;
  renderTurnos();
}

function renderPaginador() {
  const { number, totalPages, totalElements } = state.pagina;
  $('#paginadorTurnos').hidden = totalPages <= 1;
  $('#paginadorInfo').textContent = `Página ${number + 1} de ${totalPages} · ${totalElements} turnos`;
  $('#paginaAnterior').disabled = number === 0;
  $('#paginaSiguiente').disabled = number >= totalPages - 1;
}

$('#paginaAnterior').addEventListener('click', () => cargarTurnos(state.pagina.number - 1).catch((err) => toast(err.message, true)));
$('#paginaSiguiente').addEventListener('click', () => cargarTurnos(state.pagina.number + 1).catch((err) => toast(err.message, true)));

function renderTurnos() {
  renderMisTurnos();
  renderPaginador();
  const tbody = $('#tablaTurnos');
  const turnos = state.pagina.content;

  if (!turnos.length) return filaVacia(tbody, 6, 'No hay turnos');
  tbody.innerHTML = turnos.map((t) => {
    const servicio = servicioPorId(t.idServicio);
    const fin = finDeTurno(fechaDeBackend(t.diaHorario), minutosDeTurno(t));
    return `
    <tr>
      <td>${formatearFecha(t.diaHorario)}${servicio ? ` – ${hhmm(fin)}` : ''}</td>
      <td>${esc(servicio?.nombre ?? '—')}</td>
      <td>${esc(nombreCompleto(usuarioPorId(t.idManicurista)) || `#${t.idManicurista}`)}</td>
      <td>${esc(nombreCompleto(usuarioPorId(t.idCliente)) || `#${t.idCliente}`)}</td>
      <td><span class="badge ${ESTADOS_TURNO[t.estado].clase}">${ESTADOS_TURNO[t.estado].texto}</span></td>
      <td class="acciones">${accionesDeTurno(t)}</td>
    </tr>`;
  }).join('');
}

// ---------- Agenda (manicuristas) ----------

const pctAgenda = (min) => ((min - APERTURA_MIN) / (CIERRE_MIN - APERTURA_MIN)) * 100;

// Pasa los bloques del backend a minutos del día, recortados al horario de atención
// (los que quedan fuera se descartan)
function bloquesDelDia(bloques, fecha) {
  const inicioDia = new Date(`${fecha}T00:00:00`);
  const minutosDelDia = (date) => Math.round((date - inicioDia) / 60000);
  return bloques
    .map((b) => ({
      ...b,
      desde: Math.max(minutosDelDia(fechaDeBackend(b.inicio)), APERTURA_MIN),
      hasta: Math.min(minutosDelDia(fechaDeBackend(b.fin)), CIERRE_MIN),
    }))
    .filter((b) => b.hasta > b.desde);
}

// Los tramos dentro de la barra. Rojo = turnos reservados; gris = bloqueos y horas fuera del horario de trabajo
function htmlBloques(bloques, conTexto) {
  return bloques.map((b) => {
    const turno = b.tipo === 'TURNO';
    const rango = `${minutosAHHMM(b.desde)}–${minutosAHHMM(b.hasta)}`;
    const titulo = turno ? `${rango} · ${b.cliente} · ${b.servicio}` : `${rango} · ${b.servicio}`;
    const texto = conTexto && b.hasta - b.desde >= 60 ? (turno ? rango : b.servicio) : '';
    return `
    <div class="agenda-bloque ${turno ? '' : 'nodisp'}" style="left:${pctAgenda(b.desde)}%;width:${pctAgenda(b.hasta) - pctAgenda(b.desde)}%"
         title="${esc(titulo)}">${esc(texto)}</div>`;
  }).join('');
}

// Línea de "ahora": solo si se está mirando el día de hoy y estamos dentro del horario de atención
function htmlAhora(fecha) {
  if (fecha !== hoy()) return '';
  const ahora = new Date();
  const min = ahora.getHours() * 60 + ahora.getMinutes();
  if (min < APERTURA_MIN || min > CIERRE_MIN) return '';
  return `<div class="agenda-ahora" style="left:${pctAgenda(min)}%" title="Ahora (${hhmm(ahora)})"></div>`;
}

// El eje de las horas (06, 07, ... 23) que va debajo de la barra
function htmlEjeHoras() {
  let horas = '';
  for (let h = APERTURA_MIN / 60; h <= CIERRE_MIN / 60; h++) {
    horas += `<span style="left:${pctAgenda(h * 60)}%">${pad2(h)}</span>`;
  }
  return horas;
}

// Un día con su barra de 06:00 a 23:00 (verde = disponible, rojo = ocupado) y el detalle de abajo
function renderAgenda(bloques, fecha) {
  const todos = bloquesDelDia(bloques, fecha);
  const ocupados = todos.filter((b) => b.tipo === 'TURNO');
  const noDisponibles = todos.filter((b) => b.tipo === 'NO_DISPONIBLE');

  $('#agendaFechaGrande').textContent = textoDeDia(fecha);
  $('#agendaBarra').innerHTML = htmlBloques(todos, true) + htmlAhora(fecha);
  $('#agendaHoras').innerHTML = htmlEjeHoras();

  // Tramos libres = lo que queda entre los ocupados
  const libres = [];
  let cursor = APERTURA_MIN;
  for (const b of [...todos].sort((a, c) => a.desde - c.desde)) {
    if (b.desde > cursor) libres.push([cursor, b.desde]);
    cursor = Math.max(cursor, b.hasta);
  }
  if (cursor < CIERRE_MIN) libres.push([cursor, CIERRE_MIN]);

  $('#agendaDetalle').innerHTML = `
    <div>
      <h3>Ocupado</h3>
      <ul>${ocupados.length ? ocupados.map((b) => `
        <li class="ocupado">${minutosAHHMM(b.desde)}–${minutosAHHMM(b.hasta)} · ${esc(b.cliente)} · ${esc(b.servicio)}</li>`).join('')
        : '<li class="libre">Sin turnos reservados</li>'}</ul>
    </div>
    ${noDisponibles.length ? `
    <div>
      <h3>No disponible</h3>
      <ul>${noDisponibles.map((b) => `
        <li class="nodisp">${minutosAHHMM(b.desde)}–${minutosAHHMM(b.hasta)} · ${esc(b.servicio)}</li>`).join('')}</ul>
    </div>` : ''}
    <div>
      <h3>Disponible</h3>
      <ul>${libres.length ? libres.map(([d, h]) => `
        <li class="libre">${minutosAHHMM(d)}–${minutosAHHMM(h)}</li>`).join('')
        : '<li class="ocupado">No quedan horarios libres</li>'}</ul>
    </div>`;
}

// ---- Semana ----

const DIAS_CORTOS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];

// Suma (o resta) días a una fecha 'YYYY-MM-DD' y devuelve otra en el mismo formato
function sumarDias(fecha, dias) {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  return new Date(anio, mes - 1, dia + dias).toLocaleDateString('en-CA');
}

// 'Jueves 29 de octubre' (según el navegador, el formato trae una coma después del día: se saca)
function textoDeDia(fecha) {
  const texto = new Date(`${fecha}T00:00:00`)
    .toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' })
    .replace(',', '');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

// 'jue 29/10'
function etiquetaDeDia(fecha) {
  const d = new Date(`${fecha}T00:00:00`);
  return `${DIAS_CORTOS[d.getDay()]} ${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}`;
}

// Primer día de la semana que se está mirando; null = hoy (así sigue siendo "hoy" aunque pase la medianoche)
let semanaDesde = null;

function renderSemana(dias) {
  const desde = dias[0].fecha;
  const hasta = dias[dias.length - 1].fecha;
  $('#semanaRango').textContent = `${etiquetaDeDia(desde)} al ${etiquetaDeDia(hasta)}`;

  const filas = dias.map(({ fecha, bloques }) => {
    const todos = bloquesDelDia(bloques, fecha);
    const turnos = todos.filter((b) => b.tipo === 'TURNO').length;
    const resumen = turnos === 0 ? 'Sin turnos' : `${turnos} ${turnos === 1 ? 'turno' : 'turnos'}`;
    return `
      <button type="button" class="semana-fila ${fecha === hoy() ? 'hoy' : ''}" data-agenda-dia="${fecha}"
              title="Ver el detalle de ${esc(textoDeDia(fecha))}">
        <span class="semana-dia">${etiquetaDeDia(fecha)}<small>${resumen}</small></span>
        <div class="agenda-barra chica">${htmlBloques(todos, false)}${htmlAhora(fecha)}</div>
      </button>`;
  }).join('');

  $('#semanaGrilla').innerHTML = `
    <div class="semana-fila"><span></span><div class="agenda-horas">${htmlEjeHoras()}</div></div>
    ${filas}`;
}

function avisarErrorDeAgenda(e) {
  toast(e.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : e.message, true);
}

async function cargarSemana() {
  if (!esManicurista()) return;
  try {
    renderSemana(await get(`/turnos/agenda/semana?desde=${semanaDesde ?? hoy()}`));
  } catch (e) {
    avisarErrorDeAgenda(e);
  }
}

// Sin fecha elegida se ve la semana y el día de hoy; con una fecha elegida, solo ese día
async function cargarAgenda() {
  const idManicurista = $('#agendaManicurista').value;
  if (!esManicurista() || !idManicurista) return;
  const fechaElegida = $('#agendaFecha').value;
  const fecha = fechaElegida || hoy();
  $('#agendaSemana').hidden = Boolean(fechaElegida);
  $('#agendaVerSemana').hidden = !fechaElegida;
  try {
    renderAgenda(await get(`/turnos/agenda?fecha=${fecha}&idManicurista=${idManicurista}`), fecha);
  } catch (e) {
    avisarErrorDeAgenda(e);
  }
  if (!fechaElegida) await cargarSemana();
}

$('#agendaManicurista').addEventListener('change', cargarAgenda);
$('#agendaFecha').addEventListener('change', cargarAgenda);

// Flechas: de 7 en 7 días. "Hoy" vuelve a la semana actual
function moverSemana(dias) {
  semanaDesde = sumarDias(semanaDesde ?? hoy(), dias);
  cargarSemana();
}
$('#semanaAnterior').addEventListener('click', () => moverSemana(-7));
$('#semanaSiguiente').addEventListener('click', () => moverSemana(7));
$('#semanaHoy').addEventListener('click', () => { semanaDesde = null; cargarSemana(); });

// Tocar un día de la semana abre el detalle de ese día (como si lo hubiera buscado con la fecha)
$('#semanaGrilla').addEventListener('click', (e) => {
  const fila = e.target.closest('[data-agenda-dia]');
  if (!fila) return;
  $('#agendaFecha').value = fila.dataset.agendaDia;
  cargarAgenda();
});

// Vuelve a la vista de la semana: se borra la fecha buscada
$('#agendaVerSemana').addEventListener('click', () => {
  $('#agendaFecha').value = '';
  cargarAgenda();
});

async function cancelarDesdeClick(e) {
  if (e.target.dataset.reprogramar) return abrirReprogramar(e.target.dataset.reprogramar);
  const id = e.target.dataset.cancelar;
  if (!id || !confirm('¿Cancelar este turno?')) return;
  e.target.disabled = true;
  try {
    await api(`/turnos/${id}/cancelar`, { method: 'PUT' });
    toast('Turno cancelado');
    await cargarTurnos();
    cargarAgenda();
  } catch (err) {
    toast(err.message, true);
    e.target.disabled = false;
  }
}

$('#tablaTurnos').addEventListener('click', cancelarDesdeClick);
$('#tarjetasTurnos').addEventListener('click', cancelarDesdeClick);

// Marca (o corrige) que el cliente no se presentó a un turno que ya pasó
async function asistenciaDesdeClick(e) {
  const id = e.target.dataset.asistencia;
  if (!id) return;
  const noAsistio = e.target.dataset.noAsistio === 'true';
  if (noAsistio && !confirm('¿Marcar que el cliente no asistió a este turno?')) return;
  e.target.disabled = true;
  try {
    await api(`/turnos/${id}/asistencia`, { method: 'PUT', body: JSON.stringify({ noAsistio }) });
    toast(noAsistio ? 'Turno marcado como "No asistió"' : 'Turno corregido: el cliente sí asistió');
    await cargarTurnos();
  } catch (err) {
    toast(err.message, true);
    e.target.disabled = false;
  }
}

$('#tablaTurnos').addEventListener('click', asistenciaDesdeClick);

// ---------- Reprogramar un turno ----------

let turnoReprogramando = null;
let horaReprogramar = '';

function abrirReprogramar(id) {
  const t = [...state.turnos, ...state.pagina.content].find((x) => String(x.id) === String(id));
  if (!t) return;
  turnoReprogramando = t;
  horaReprogramar = '';

  const servicio = servicioPorId(t.idServicio);
  const form = $('#formReprogramar');
  form.reset();
  form.fecha.min = hoy();
  $('#reprogramarResumen').textContent =
    `${servicio?.nombre ?? 'Turno'} con ${nombreCompleto(usuarioPorId(t.idManicurista))}` +
    (esCliente() ? '' : ` para ${nombreCompleto(usuarioPorId(t.idCliente))}`) +
    ` · Ahora: ${formatearFecha(t.diaHorario)}`;
  $('#reprogramarHorarios').innerHTML = '<p class="empty">Elegí la nueva fecha para ver los horarios libres</p>';
  $('#reprogramarError').hidden = true;
  $('#reprogramarConfirmar').disabled = true;
  $('#modalReprogramar').showModal();
}

$('#formReprogramar').fecha.addEventListener('change', async (e) => {
  const t = turnoReprogramando;
  const fecha = e.target.value;
  horaReprogramar = '';
  $('#reprogramarConfirmar').disabled = true;
  $('#reprogramarError').hidden = true;
  if (!t || !fecha) return;

  const minutos = minutosDeTurno(t);
  const clave = `${t.id}|${fecha}`;
  abrirReprogramar.clave = clave;
  const libres = await calcularHorariosLibres({
    idManicurista: t.idManicurista, minutos, fecha, idCliente: t.idCliente, excluirTurno: t.id,
  });
  if (abrirReprogramar.clave !== clave) return;
  $('#reprogramarHorarios').innerHTML = htmlHorarios(libres, minutos);
});

$('#reprogramarHorarios').addEventListener('click', (e) => {
  const slot = e.target.closest('.slot');
  if (!slot) return;
  horaReprogramar = slot.dataset.hora;
  document.querySelectorAll('#reprogramarHorarios .slot.selected').forEach((el) => el.classList.remove('selected'));
  slot.classList.add('selected');
  $('#reprogramarConfirmar').disabled = false;
  $('#reprogramarError').hidden = true;
});

$('#reprogramarCancelar').addEventListener('click', () => $('#modalReprogramar').close());

$('#formReprogramar').addEventListener('submit', async (e) => {
  e.preventDefault();
  const t = turnoReprogramando;
  const fecha = e.target.fecha.value;
  if (!t || !fecha || !horaReprogramar) return;

  const btn = $('#reprogramarConfirmar');
  btn.disabled = true;
  try {
    await api(`/turnos/${t.id}/reprogramar`, { method: 'PUT', body: JSON.stringify({ diaHorario: `${fecha} ${horaReprogramar}:00` }) });
    $('#modalReprogramar').close();
    toast('Turno reprogramado');
    await cargarTurnos();
    cargarAgenda();
  } catch (err) {
    const error = $('#reprogramarError');
    error.textContent = err.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : err.message;
    error.hidden = false;
    btn.disabled = false;
  }
});

// ---------- Carga de datos ----------

async function cargar(clave, path, render) {
  state[clave] = await get(path);
  render();
}

async function cargarTodo() {
  const status = $('#apiStatus');
  const resultados = await Promise.allSettled([
    cargar('tipos', '/tiposUsuario', renderTipos),
    cargar('usuarios', '/usuarios', renderUsuarios),
    cargar('servicios', '/servicios', renderServicios),
    cargar('ofertas', '/servicios/ofertas', renderServicios),
    cargarTurnos(),
  ]);
  // Los turnos y usuarios muestran nombres de otras tablas: re-render cuando todo llegó
  renderUsuarios();
  renderTurnos();
  cargarAgenda();
  renderHorarios();
  cargarDisponibilidad();

  const fallidos = resultados.filter((r) => r.status === 'rejected');
  status.className = 'status ' + (fallidos.length === resultados.length ? 'down' : 'ok');
  status.title = fallidos.length ? `Errores: ${fallidos.map((f) => f.reason.message).join(', ')}` : 'Conectado';
  if (fallidos.length === resultados.length) {
    toast('No se pudo conectar con el backend. ¿Está corriendo en ' + apiInput.value + '?', true);
  } else if (fallidos.length) {
    toast('Algunos datos no se pudieron cargar: ' + fallidos[0].reason.message, true);
  }
}

// ---------- Formularios ----------

// ---------- Horarios libres (clientes) ----------

const PASO_HORARIOS_MIN = 30;

// Horarios (cada 30 min) en los que entra un turno de `minutos` ese día con esa manicurista:
// descarta lo pasado, los bloqueos / horas fuera de su horario de trabajo y los turnos que se pisan.
// excluirTurno = turno que se está reprogramando (no cuenta como ocupado)
async function calcularHorariosLibres({ idManicurista, minutos, fecha, idCliente, excluirTurno }) {
  let noDisponible = [];
  try {
    const extra = excluirTurno ? `&excluirTurno=${excluirTurno}` : '';
    noDisponible = (await get(`/disponibilidad/manicurista/${idManicurista}?fecha=${fecha}${extra}`))
      .map((i) => ({ desde: horaAMinutos(i.desde), hasta: horaAMinutos(i.hasta) }));
  } catch {}

  // Turnos propios del cliente (el backend solo le devuelve los suyos)
  const delCliente = state.turnos.filter((t) => t.activo && t.idCliente === idCliente && t.id !== excluirTurno);
  const ahora = new Date();

  const libres = [];
  for (let min = APERTURA_MIN; min + minutos <= CIERRE_MIN; min += PASO_HORARIOS_MIN) {
    const inicio = new Date(`${fecha}T${minutosAHHMM(min)}`);
    if (inicio < ahora) continue;
    if (noDisponible.some((i) => min < i.hasta && min + minutos > i.desde)) continue;
    const fin = finDeTurno(inicio, minutos);
    if (seSuperpone(delCliente, inicio, fin)) continue;
    libres.push({ min, fin });
  }
  return libres;
}

const htmlHorarios = (libres, minutos) => libres.length
  ? `<h3>Horarios libres (${minutos} min cada turno)</h3>
     <div class="slots-lista">${libres.map((l) => `
       <button type="button" class="slot" data-hora="${minutosAHHMM(l.min)}">
         ${minutosAHHMM(l.min)}<small>hasta ${hhmm(l.fin)}</small>
       </button>`).join('')}</div>`
  : '<p class="empty">No hay horarios libres ese día. Probá con otra fecha.</p>';

// El cliente no escribe la hora: elige una tarjeta entre los horarios libres de la manicurista
async function renderHorarios() {
  if (!esCliente()) return;
  const f = $('#formTurno');
  const cont = $('#horariosLibres');
  f.hora.value = '';
  renderHorarios.clave = null;

  const idManicurista = Number(f.idManicurista.value);
  const servicio = servicioPorId(Number(f.idServicio.value));
  const fecha = f.fecha.value;
  if (!idManicurista || !servicio || !fecha) {
    cont.innerHTML = '<p class="empty">Elegí manicurista, servicio y fecha para ver los horarios libres</p>';
    return;
  }

  const clave = `${idManicurista}|${servicio.id}|${fecha}`;
  renderHorarios.clave = clave;
  const minutos = minutosDeOferta(idManicurista, servicio.id);
  const libres = await calcularHorariosLibres({ idManicurista, minutos, fecha, idCliente: state.sesion.id });
  if (renderHorarios.clave !== clave) return; // el usuario ya cambió la selección

  cont.innerHTML = htmlHorarios(libres, minutos);
}

$('#horariosLibres').addEventListener('click', (e) => {
  const slot = e.target.closest('.slot');
  if (!slot) return;
  $('#formTurno').hora.value = slot.dataset.hora;
  document.querySelectorAll('.slot.selected').forEach((el) => el.classList.remove('selected'));
  slot.classList.add('selected');
  $('#horariosLibres .field-error')?.remove();
});

// Al cambiar manicurista, servicio o fecha se traen los turnos al día y se recalculan los horarios
async function actualizarHorarios() {
  try { await cargarTurnos(); } catch {}
  renderHorarios();
}
for (const campo of ['idManicurista', 'idServicio', 'fecha']) {
  $('#formTurno').elements[campo].addEventListener('change', actualizarHorarios);
}
$('#formTurno').addEventListener('reset', () => setTimeout(renderHorarios));

$('#formTurno').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = e.target;
  if (esCliente() && !f.hora.value) {
    return toast('Elegí un horario libre de la lista', true);
  }
  enviarForm(f, validarTurno, async () => {
    await post('/turnos', {
      idManicurista: Number(f.idManicurista.value),
      idCliente: clienteDelTurno(f),
      idServicio: Number(f.idServicio.value),
      diaHorario: `${f.fecha.value} ${f.hora.value}:00`,
    });
    toast('Turno reservado');
    await cargarTurnos();
    cargarAgenda();
  });
});

$('#formServicio').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = e.target;
  enviarForm(f, validarServicio, async () => {
    const datos = {
      nombre: f.nombre.value.trim(),
      descripcion: f.descripcion.value.trim(),
      duracion: f.duracion.value ? minutosAHora(Number(f.duracion.value)) : null,
      precio: Number(f.precio.value),
      activo: f.activo.checked,
    };
    if (servicioEnEdicion != null) {
      await api(`/servicios/${servicioEnEdicion}`, { method: 'PUT', body: JSON.stringify(datos) });
      toast('Servicio actualizado');
      salirDeEdicionServicio();
    } else {
      const creado = await post('/servicios', datos);
      // Una manicurista que crea un servicio lo ofrece también ella, con ese precio
      if (esManicurista() && creado.activo) await post('/servicios/ofertas', { idServicio: creado.id });
      toast('Servicio guardado');
    }
    await cargar('servicios', '/servicios', renderServicios);
    await recargarOfertas();
  });
});

// -- Editar y dar de baja servicios del catálogo (admin) --

function salirDeEdicionServicio() {
  servicioEnEdicion = null;
  $('#tituloServicio').textContent = 'Nuevo servicio en el catálogo';
  $('#btnServicio').textContent = 'Guardar servicio';
  $('#btnCancelarServicio').hidden = true;
}

$('#btnCancelarServicio').addEventListener('click', () => {
  salirDeEdicionServicio();
  $('#formServicio').reset();
  limpiarErrores($('#formServicio'));
});

async function cambiarActivoServicio(id, activo) {
  const s = servicioPorId(Number(id));
  try {
    await api(`/servicios/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ nombre: s.nombre, descripcion: s.descripcion, duracion: s.duracion, precio: s.precio, activo }),
    });
    toast(activo ? 'Servicio reactivado' : 'Servicio dado de baja');
    await cargar('servicios', '/servicios', renderServicios);
  } catch (err) {
    toast(err.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : err.message, true);
  }
}

$('#listaServicios').addEventListener('click', (e) => {
  const d = e.target.dataset;
  if (d.editarServicio) {
    const s = servicioPorId(Number(d.editarServicio));
    const f = $('#formServicio');
    limpiarErrores(f);
    f.nombre.value = s.nombre;
    f.descripcion.value = s.descripcion ?? '';
    f.duracion.value = horaAMinutos(s.duracion);
    f.precio.value = s.precio;
    f.activo.checked = s.activo;
    servicioEnEdicion = s.id;
    $('#tituloServicio').textContent = `Editar servicio: ${s.nombre}`;
    $('#btnServicio').textContent = 'Guardar cambios';
    $('#btnCancelarServicio').hidden = false;
    f.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
  if (d.bajaServicio && confirm('¿Dar de baja este servicio? Deja de poder reservarse con cualquier manicurista; los turnos ya reservados se mantienen.')) {
    cambiarActivoServicio(d.bajaServicio, false);
  }
  if (d.altaServicio) cambiarActivoServicio(d.altaServicio, true);
});

$('#formUsuario').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = e.target;
  enviarForm(f, validarUsuario, async () => {
    const datos = {
      nombre: f.nombre.value.trim(),
      apellido: f.apellido.value.trim(),
      numeroContacto: f.numeroContacto.value.trim() || null,
      email: f.email.value.trim(),
      direccion: f.direccion.value.trim(),
      password: f.password.value,
    };
    if (usuarioEnEdicion != null) {
      await api(`/usuarios/${usuarioEnEdicion}`, { method: 'PUT', body: JSON.stringify(datos) });
      toast('Usuario actualizado');
      salirDeEdicionUsuario();
    } else {
      await post('/usuarios', { tipo: Number(f.tipo.value), ...datos });
      toast('Usuario guardado');
    }
    await cargar('usuarios', '/usuarios', renderUsuarios);
    renderTurnos();
  });
});

// -- Editar, dar de baja y reactivar usuarios (admin) --

function salirDeEdicionUsuario() {
  usuarioEnEdicion = null;
  $('#tituloUsuario').textContent = 'Nuevo usuario';
  $('#btnUsuario').textContent = 'Guardar usuario';
  $('#btnCancelarUsuario').hidden = true;
  $('#formUsuario').tipo.disabled = false;
  $('#formUsuario').password.required = true;
  $('#formUsuario').password.placeholder = '';
}

$('#btnCancelarUsuario').addEventListener('click', () => {
  salirDeEdicionUsuario();
  $('#formUsuario').reset();
  limpiarErrores($('#formUsuario'));
});

async function cambiarActivoUsuario(id, activo) {
  try {
    await api(`/usuarios/${id}/activo`, { method: 'PUT', body: JSON.stringify({ activo }) });
    toast(activo ? 'Usuario reactivado' : 'Usuario dado de baja');
    await cargar('usuarios', '/usuarios', renderUsuarios);
    renderTurnos();
  } catch (err) {
    toast(err.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : err.message, true);
  }
}

$('#tablaUsuarios').addEventListener('click', (e) => {
  const d = e.target.dataset;
  if (d.editarUsuario) {
    const u = state.usuarios.find((x) => String(x.id) === d.editarUsuario);
    const f = $('#formUsuario');
    limpiarErrores(f);
    f.tipo.value = u.tipo;
    f.tipo.disabled = true; // el tipo no se cambia
    f.nombre.value = u.nombre ?? '';
    f.apellido.value = u.apellido ?? '';
    f.numeroContacto.value = u.numeroContacto || '';
    f.email.value = u.email ?? '';
    f.direccion.value = u.direccion ?? '';
    f.password.value = '';
    f.password.required = false;
    f.password.placeholder = 'Vacío = no cambiarla';
    usuarioEnEdicion = u.id;
    $('#tituloUsuario').textContent = `Editar usuario: ${nombreCompleto(u)}`;
    $('#btnUsuario').textContent = 'Guardar cambios';
    $('#btnCancelarUsuario').hidden = false;
    f.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
  if (d.bajaUsuario) {
    const u = state.usuarios.find((x) => String(x.id) === d.bajaUsuario);
    if (confirm(`¿Dar de baja a ${nombreCompleto(u)}? No va a poder iniciar sesión ni recibir turnos.`)) {
      cambiarActivoUsuario(d.bajaUsuario, false);
    }
  }
  if (d.altaUsuario) cambiarActivoUsuario(d.altaUsuario, true);
});

$('#formTipo').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = e.target;
  enviarForm(f, validarTipo, async () => {
    await post('/tiposUsuario', { descripcion: f.descripcion.value.trim() });
    toast('Tipo de usuario guardado');
    await cargar('tipos', '/tiposUsuario', renderTipos);
    renderUsuarios();
  });
});

for (const filtro of ['#filtroManicurista', '#filtroMomento']) {
  $(filtro).addEventListener('change', () => cargarTurnos(0).catch((err) => toast(err.message, true)));
}

// ---------- Tabs ----------

function mostrarTab(tab) {
  document.querySelectorAll('.tab, .panel').forEach((el) => el.classList.remove('active'));
  tab.classList.add('active');
  $('#' + tab.dataset.tab).classList.add('active');
  if (tab.dataset.tab === 'agenda') cargarAgenda();
}

document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', () => mostrarTab(tab));
});

// No permitir elegir fechas pasadas (el backend las rechaza igual)
$('#formTurno [name="fecha"]').min = hoy();

// ---------- Horarios y bloqueos (manicuristas) ----------

const NOMBRES_DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
const hhmmDeBackend = (t) => String(t).slice(0, 5);

// "jueves" -> "jueves", "sábado" -> "sábados"
function diaPlural(fechaISO) {
  const dia = new Date(`${fechaISO}T00:00:00`).toLocaleDateString('es-AR', { weekday: 'long' });
  return dia.endsWith('s') ? dia : `${dia}s`;
}

function textoRepeticion(repeticion, fechaISO) {
  const fecha = new Date(`${fechaISO}T00:00:00`);
  const dia = fecha.toLocaleDateString('es-AR', { weekday: 'long' });
  const corta = fecha.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  const mes = fecha.toLocaleDateString('es-AR', { month: 'long', year: 'numeric' });
  if (repeticion === 'UNICA') return `Solo el ${dia} ${corta}`;
  if (repeticion === 'MES') return `Todos los ${diaPlural(fechaISO)} de ${mes}`;
  return `Todos los ${diaPlural(fechaISO)} desde el ${corta}`;
}

const REPETICION_SIN_FECHA = {
  UNICA: 'Solo ese día',
  MES: 'Todos los mismos días de la semana de ese mes',
  SIEMPRE: 'Todos los mismos días de la semana, para siempre',
};

// Las opciones de repetición mencionan el día elegido ("Todos los jueves de octubre de 2026")
function actualizarOpcionesRepeticion() {
  const fecha = $('#formBloqueo').fecha.value;
  for (const span of document.querySelectorAll('#formBloqueo [data-rep]')) {
    span.textContent = fecha ? textoRepeticion(span.dataset.rep, fecha) : REPETICION_SIN_FECHA[span.dataset.rep];
  }
}
$('#formBloqueo').fecha.addEventListener('input', actualizarOpcionesRepeticion);
$('#formBloqueo').fecha.min = hoy();

// -- Horario semanal --

function renderFranjas(franjas) {
  // Sin franjas cargadas se atiende todo el horario de apertura, todos los días
  const porDia = {};
  for (const fr of franjas) porDia[fr.diaSemana] = porDia[fr.diaSemana] || fr;

  $('#franjasSemana').innerHTML = NOMBRES_DIAS.map((nombre, i) => {
    const dia = i + 1;
    const fr = porDia[dia];
    const trabaja = franjas.length ? Boolean(fr) : true;
    return `
      <div class="franja-fila ${trabaja ? '' : 'off'}" data-dia="${dia}">
        <label class="check"><input type="checkbox" ${trabaja ? 'checked' : ''}> ${nombre}</label>
        <input type="time" name="desde" step="900" value="${fr ? hhmmDeBackend(fr.horaDesde) : minutosAHHMM(APERTURA_MIN)}">
        <span>a</span>
        <input type="time" name="hasta" step="900" value="${fr ? hhmmDeBackend(fr.horaHasta) : minutosAHHMM(CIERRE_MIN)}">
      </div>`;
  }).join('');
}

$('#franjasSemana').addEventListener('change', (e) => {
  if (e.target.type === 'checkbox') e.target.closest('.franja-fila').classList.toggle('off', !e.target.checked);
});

$('#formFranjas').addEventListener('submit', async (e) => {
  e.preventDefault();
  const franjas = [];
  for (const fila of document.querySelectorAll('#franjasSemana .franja-fila')) {
    if (!fila.querySelector('input[type="checkbox"]').checked) continue;
    const desde = fila.querySelector('[name="desde"]').value;
    const hasta = fila.querySelector('[name="hasta"]').value;
    const nombre = NOMBRES_DIAS[Number(fila.dataset.dia) - 1];
    if (!desde || !hasta) return toast(`${nombre}: completá desde y hasta`, true);
    if (desde >= hasta) return toast(`${nombre}: la hora de inicio tiene que ser anterior a la de fin`, true);
    if (horaAMinutos(desde) < APERTURA_MIN || horaAMinutos(hasta) > CIERRE_MIN) {
      return toast(`${nombre}: el horario tiene que estar entre las ${minutosAHHMM(APERTURA_MIN)} y las ${minutosAHHMM(CIERRE_MIN)}`, true);
    }
    franjas.push({ diaSemana: Number(fila.dataset.dia), horaDesde: desde, horaHasta: hasta });
  }
  if (!franjas.length && !confirm('No marcaste ningún día: se va a considerar que atendés todos los días de 06:00 a 23:00. ¿Continuar?')) return;

  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  try {
    renderFranjas(await api('/disponibilidad/franjas', { method: 'PUT', body: JSON.stringify(franjas) }));
    toast('Horario de trabajo guardado');
    cargarAgenda();
  } catch (err) {
    toast(err.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : err.message, true);
  } finally {
    btn.disabled = false;
  }
});

// -- Bloqueos --

let bloqueos = [];
let bloqueoEnEdicion = null;

function renderBloqueos() {
  const cont = $('#listaBloqueos');
  if (!bloqueos.length) {
    cont.innerHTML = '<p class="empty">No tenés horarios bloqueados</p>';
    return;
  }
  cont.innerHTML = bloqueos.map((b) => `
    <article class="turno-card">
      <div class="turno-fecha">${hhmmDeBackend(b.horaDesde)} – ${hhmmDeBackend(b.horaHasta)} hs</div>
      <div class="turno-hora">${esc(textoRepeticion(b.repeticion, b.fecha))}</div>
      <dl><dt>Motivo</dt><dd>${esc(b.motivo || '—')}</dd></dl>
      <div class="bloqueo-acciones">
        <button class="btn-link editar" data-editar="${b.id}">Editar</button>
        <button class="btn-link" data-eliminar="${b.id}">Eliminar</button>
      </div>
    </article>`).join('');
}

async function cargarDisponibilidad() {
  if (!esManicurista()) return;
  try {
    const [franjas, lista] = await Promise.all([get('/disponibilidad/franjas'), get('/disponibilidad/bloqueos')]);
    renderFranjas(franjas);
    bloqueos = lista;
    renderBloqueos();
  } catch (e) {
    toast(e.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : e.message, true);
  }
}

function salirDeEdicion() {
  bloqueoEnEdicion = null;
  $('#tituloBloqueo').textContent = 'Bloquear un horario';
  $('#btnBloqueo').textContent = 'Bloquear horario';
  $('#btnCancelarEdicion').hidden = true;
}

function validarBloqueo(f) {
  const e = {};
  if (!f.fecha.value) e.fecha = 'Elegí una fecha';
  else if (f.fecha.value < hoy()) e.fecha = 'La fecha no puede ser anterior a hoy';
  if (!f.horaDesde.value) e.horaDesde = 'Elegí la hora de inicio';
  if (!f.horaHasta.value) e.horaHasta = 'Elegí la hora de fin';
  if (e.horaDesde || e.horaHasta) return e;

  if (f.horaDesde.value >= f.horaHasta.value) e.horaHasta = 'Tiene que ser posterior a la hora de inicio';
  else if (horaAMinutos(f.horaDesde.value) < APERTURA_MIN || horaAMinutos(f.horaHasta.value) > CIERRE_MIN) {
    e.horaDesde = `Tiene que estar entre las ${minutosAHHMM(APERTURA_MIN)} y las ${minutosAHHMM(CIERRE_MIN)}`;
  }
  return e;
}

$('#formBloqueo').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = e.target;
  enviarForm(f, validarBloqueo, async () => {
    const datos = {
      fecha: f.fecha.value,
      horaDesde: f.horaDesde.value,
      horaHasta: f.horaHasta.value,
      repeticion: f.repeticion.value,
      motivo: f.motivo.value.trim(),
    };
    const editando = bloqueoEnEdicion;
    if (editando) await api(`/disponibilidad/bloqueos/${editando}`, { method: 'PUT', body: JSON.stringify(datos) });
    else await post('/disponibilidad/bloqueos', datos);
    toast(editando ? 'Bloqueo actualizado' : 'Horario bloqueado');
    salirDeEdicion();
    await cargarDisponibilidad();
    cargarAgenda();
  });
});

$('#formBloqueo').addEventListener('reset', () => setTimeout(actualizarOpcionesRepeticion));

$('#btnCancelarEdicion').addEventListener('click', () => {
  salirDeEdicion();
  $('#formBloqueo').reset();
  limpiarErrores($('#formBloqueo'));
});

$('#listaBloqueos').addEventListener('click', async (e) => {
  const idEditar = e.target.dataset.editar;
  const idEliminar = e.target.dataset.eliminar;

  if (idEditar) {
    const b = bloqueos.find((x) => String(x.id) === idEditar);
    const f = $('#formBloqueo');
    limpiarErrores(f);
    f.fecha.value = b.fecha;
    f.horaDesde.value = hhmmDeBackend(b.horaDesde);
    f.horaHasta.value = hhmmDeBackend(b.horaHasta);
    f.motivo.value = b.motivo || '';
    f.repeticion.value = b.repeticion;
    actualizarOpcionesRepeticion();
    bloqueoEnEdicion = b.id;
    $('#tituloBloqueo').textContent = 'Editar bloqueo';
    $('#btnBloqueo').textContent = 'Guardar cambios';
    $('#btnCancelarEdicion').hidden = false;
    f.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  if (idEliminar && confirm('¿Eliminar este bloqueo? Se liberan los horarios para nuevos turnos.')) {
    try {
      await api(`/disponibilidad/bloqueos/${idEliminar}`, { method: 'DELETE' });
      toast('Bloqueo eliminado');
      if (String(bloqueoEnEdicion) === idEliminar) {
        salirDeEdicion();
        $('#formBloqueo').reset();
      }
      await cargarDisponibilidad();
      cargarAgenda();
    } catch (err) {
      toast(err.message, true);
    }
  }
});

actualizarOpcionesRepeticion();

// ---------- Sesión ----------

function guardarSesion() {
  try {
    if (state.sesion) sessionStorage.setItem('sesion', JSON.stringify(state.sesion));
    else sessionStorage.removeItem('sesion');
  } catch {}
}

// Muestra solo lo que corresponde al rol: el admin gestiona usuarios y ve todos los turnos,
// las manicuristas su agenda y horarios, y los clientes sus turnos
function iniciarSesion(sesion) {
  state.sesion = sesion;
  guardarSesion();

  document.querySelectorAll('[data-solo]').forEach((el) => {
    el.hidden = !el.dataset.solo.split(' ').includes(sesion.rol);
  });
  const ETIQUETAS_ROL = { MANICURISTA: 'Manicurista', CLIENTE: 'Cliente', ADMIN: 'Administrador' };
  $('#usuarioActual').textContent = `${nombreCompleto(sesion)} · ${ETIQUETAS_ROL[sesion.rol] ?? sesion.rol}`;
  $('#usuarioActual').hidden = false;
  $('#btnLogout').hidden = false;
  $('#loginOverlay').hidden = true;

  // Sin fecha elegida la agenda muestra la semana y el día de hoy
  $('#agendaFecha').value = '';
  semanaDesde = null;
  const TAB_INICIAL = { MANICURISTA: 'agenda', CLIENTE: 'misTurnos', ADMIN: 'turnos' };
  mostrarTab($(`.tab[data-tab="${TAB_INICIAL[sesion.rol] ?? 'turnos'}"]`));
  cargarTodo();
}

function cerrarSesion() {
  state.sesion = null;
  guardarSesion();
  for (const clave of ['usuarios', 'turnos', 'ofertas']) state[clave] = [];
  document.querySelectorAll('form').forEach((form) => { form.reset(); limpiarErrores(form); });
  $('#agendaManicurista').innerHTML = '';
  $('#usuarioActual').hidden = true;
  $('#btnLogout').hidden = true;
  $('#loginOverlay').hidden = false;
  mostrarFormularioAcceso('login');
}

$('#formLogin').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  limpiarErrores(f);
  const errores = {};
  if (!f.email.value.trim()) errores.email = 'Ingresá tu email';
  if (!f.password.value) errores.password = 'Ingresá tu contraseña';
  if (Object.keys(errores).length) return mostrarErrores(f, errores);

  const btn = f.querySelector('button[type="submit"]');
  btn.disabled = true;
  try {
    const sesion = await post('/auth/login', { email: f.email.value.trim(), password: f.password.value });
    f.reset();
    iniciarSesion(sesion);
  } catch (err) {
    mostrarErrores(f, err.errores ?? {});
    toast(err.message === 'Failed to fetch' ? 'No se pudo conectar con el backend' : err.message, true);
  } finally {
    btn.disabled = false;
  }
});

// ---------- Registro (solo desde la pantalla de login) ----------

function mostrarFormularioAcceso(cual) {
  $('#formLogin').hidden = cual !== 'login';
  $('#formRegistro').hidden = cual !== 'registro';
}
$('#irARegistro').addEventListener('click', () => mostrarFormularioAcceso('registro'));
$('#irALogin').addEventListener('click', () => mostrarFormularioAcceso('login'));

// La dirección solo la piden las manicuristas
$('#formRegistro').addEventListener('change', (e) => {
  if (e.target.name === 'rol') $('#campoDireccion').hidden = e.target.value !== 'MANICURISTA';
});
$('#formRegistro').addEventListener('reset', () => setTimeout(() => { $('#campoDireccion').hidden = true; }));

function validarRegistro(f) {
  const e = {};
  for (const campo of ['nombre', 'apellido']) {
    const valor = f[campo].value.trim();
    const etiqueta = campo === 'nombre' ? 'El nombre' : 'El apellido';
    if (!valor) e[campo] = `${etiqueta} es obligatorio`;
    else if (!LETRAS.test(valor)) e[campo] = `${etiqueta} solo puede tener letras`;
  }

  const email = f.email.value.trim();
  if (!email) e.email = 'El email es obligatorio';
  else if (!EMAIL.test(email)) e.email = 'El email no tiene un formato válido';

  const telefono = f.numeroContacto.value.trim();
  if (errorTelefono(telefono)) e.numeroContacto = errorTelefono(telefono);

  if (f.password.value.length < 6) e.password = 'La contraseña tiene que tener al menos 6 caracteres';
  else if (f.password.value !== f.password2.value) e.password2 = 'Las contraseñas no coinciden';
  return e;
}

$('#formRegistro').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = e.target;
  enviarForm(f, validarRegistro, async () => {
    const sesion = await post('/auth/registro', {
      rol: f.rol.value,
      nombre: f.nombre.value.trim(),
      apellido: f.apellido.value.trim(),
      email: f.email.value.trim(),
      numeroContacto: f.numeroContacto.value.trim() || null,
      direccion: f.rol.value === 'MANICURISTA' ? f.direccion.value.trim() : '',
      password: f.password.value,
    });
    iniciarSesion(sesion);
    toast('¡Cuenta creada! Ya iniciaste sesión');
  });
});

$('#btnLogout').addEventListener('click', async () => {
  try { await post('/auth/logout', {}); } catch {}
  cerrarSesion();
});

// Recuperar la sesión si se recarga la página
let sesionGuardada = null;
try { sesionGuardada = JSON.parse(sessionStorage.getItem('sesion')); } catch {}
if (sesionGuardada?.token) iniciarSesion(sesionGuardada);
else cerrarSesion();
