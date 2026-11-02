package com.sealease.backend.department.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.department.dto.DepartmentDtos.AssignStaffRequest;
import com.sealease.backend.department.dto.DepartmentDtos.CreateDepartmentRequest;
import com.sealease.backend.department.dto.DepartmentDtos.DepartmentDetail;
import com.sealease.backend.department.dto.DepartmentDtos.DepartmentRole;
import com.sealease.backend.department.dto.DepartmentDtos.DepartmentSummary;
import com.sealease.backend.department.dto.DepartmentDtos.Employee;
import com.sealease.backend.department.dto.DepartmentDtos.HistoryEntry;
import com.sealease.backend.department.dto.DepartmentDtos.StaffDepartment;
import com.sealease.backend.department.dto.DepartmentDtos.StaffMember;
import com.sealease.backend.department.dto.DepartmentDtos.UpdateDepartmentRequest;
import com.sealease.backend.department.entity.Department;
import com.sealease.backend.department.repository.DepartmentRepository;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Departments and who works in them. An assignment never changes what anyone may do, so it needs no session
 * revocation. Those who cannot manage departments see only the ones they belong to.
 */
@Service
public class DepartmentService {

	private static final String ENTITY = "DEPARTMENT";

	private static final String NAME = "trim(u.first_name || ' ' || u.last_name)";

	private static final String SUMMARY = """
			SELECT d.id, d.code, d.name, d.description, d.active, d.head_user_id, d.version,
			       (SELECT %s FROM users u WHERE u.id = d.head_user_id) AS head_name,
			       (SELECT count(*) FROM staff_departments s WHERE s.department_id = d.id) AS employees,
			       (SELECT count(*) FROM roles r WHERE r.department_id = d.id) AS role_count
			FROM departments d
			""".formatted(NAME);

	private final DepartmentRepository departments;
	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public DepartmentService(DepartmentRepository departments, JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.departments = departments;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<DepartmentSummary> list(AuthenticatedUser viewer) {
		if (seesAll(viewer)) {
			return jdbc.query(SUMMARY + " ORDER BY d.name", DepartmentService::summary);
		}
		return jdbc.query(SUMMARY + """
				 WHERE EXISTS (SELECT 1 FROM staff_departments s WHERE s.department_id = d.id AND s.user_id = ?)
				 ORDER BY d.name""", DepartmentService::summary, viewer.userId());
	}

	@Transactional(readOnly = true)
	public DepartmentDetail get(AuthenticatedUser viewer, UUID id) {
		if (!seesAll(viewer) && !belongs(viewer.userId(), id)) {
			// The same answer as a department that does not exist.
			throw new ResourceNotFoundException("Department", id);
		}
		DepartmentSummary department = jdbc.query(SUMMARY + " WHERE d.id = ?", DepartmentService::summary, id).stream()
			.findFirst().orElseThrow(() -> new ResourceNotFoundException("Department", id));
		List<Employee> employees = jdbc.query("""
				SELECT u.id, %s AS name, u.email, s.primary_assignment, s.assigned_at
				FROM staff_departments s JOIN users u ON u.id = s.user_id
				WHERE s.department_id = ? ORDER BY name""".formatted(NAME),
				(rs, n) -> new Employee(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
						rs.getBoolean("primary_assignment"), rs.getObject("id", UUID.class).equals(department.headUserId()),
						rs.getTimestamp("assigned_at").toInstant()), id);
		List<DepartmentRole> roles = jdbc.query("""
				SELECT r.id, r.name, r.description,
				       (SELECT count(*) FROM user_roles ur WHERE ur.role_id = r.id) AS employees
				FROM roles r WHERE r.department_id = ? ORDER BY r.name""",
				(rs, n) -> new DepartmentRole(rs.getObject("id", UUID.class), rs.getString("name"),
						rs.getString("description"), rs.getLong("employees")), id);
		List<HistoryEntry> history = jdbc.query("""
				SELECT h.action, h.detail, h.created_at, %s AS actor
				FROM department_history h LEFT JOIN users u ON u.id = h.actor_id
				WHERE h.department_id = ? ORDER BY h.created_at DESC LIMIT 100""".formatted(NAME),
				(rs, n) -> new HistoryEntry(rs.getString("action"), rs.getString("detail"), rs.getString("actor"),
						rs.getTimestamp("created_at").toInstant()), id);
		return new DepartmentDetail(department, employees, roles, history);
	}

	@Transactional
	public DepartmentSummary create(UUID actorId, CreateDepartmentRequest request) {
		if (departments.existsByCode(request.code())) {
			throw new BusinessException(ErrorCode.CONFLICT, "A department with this code already exists");
		}
		Department department = departments.saveAndFlush(new Department(request.code(), request.name().strip(),
				request.description() == null ? "" : request.description().strip()));
		record(actorId, department.getId(), AuditAction.DEPARTMENT_CREATED, "CREATED", department.getName());
		return summaryOf(department.getId());
	}

	@Transactional
	public DepartmentSummary update(UUID actorId, UUID id, UpdateDepartmentRequest request) {
		Department department = load(id);
		requireVersion(department, request.version());
		department.update(request.name().strip(), request.description() == null ? "" : request.description().strip());
		departments.flush();
		record(actorId, id, AuditAction.DEPARTMENT_UPDATED, "UPDATED", department.getName());
		return summaryOf(id);
	}

	@Transactional
	public DepartmentSummary setActive(UUID actorId, UUID id, boolean active) {
		Department department = load(id);
		department.setActive(active);
		departments.flush();
		record(actorId, id, AuditAction.DEPARTMENT_UPDATED, active ? "ACTIVATED" : "DEACTIVATED", department.getName());
		return summaryOf(id);
	}

	@Transactional
	public DepartmentSummary setHead(UUID actorId, UUID id, UUID userId) {
		Department department = load(id);
		if (userId != null && !belongs(userId, id)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The head must be assigned to the department first");
		}
		department.setHeadUserId(userId);
		departments.flush();
		record(actorId, id, AuditAction.DEPARTMENT_UPDATED, "HEAD_CHANGED", nameOf(userId));
		return summaryOf(id);
	}

	/** Puts an employee in a department, or changes whether it is their primary one. */
	@Transactional
	public DepartmentSummary assign(UUID actorId, UUID id, AssignStaffRequest request) {
		Department department = load(id);
		if (!department.isActive()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This department is inactive");
		}
		requireStaff(request.userId());
		if (request.primary()) {
			jdbc.update("UPDATE staff_departments SET primary_assignment = FALSE WHERE user_id = ? AND primary_assignment",
					request.userId());
		}
		jdbc.update("""
				INSERT INTO staff_departments (user_id, department_id, primary_assignment, assigned_by, assigned_at)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (user_id, department_id) DO UPDATE SET primary_assignment = EXCLUDED.primary_assignment""",
				request.userId(), id, request.primary(), actorId, Timestamp.from(clock.instant()));
		record(actorId, id, AuditAction.DEPARTMENT_STAFF_CHANGED, "EMPLOYEE_ASSIGNED", nameOf(request.userId()));
		return summaryOf(id);
	}

	@Transactional
	public DepartmentSummary unassign(UUID actorId, UUID id, UUID userId) {
		Department department = load(id);
		if (userId.equals(department.getHeadUserId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Choose another head before removing this one");
		}
		if (jdbc.update("DELETE FROM staff_departments WHERE user_id = ? AND department_id = ?", userId, id) == 0) {
			throw new ResourceNotFoundException("Department assignment", userId);
		}
		record(actorId, id, AuditAction.DEPARTMENT_STAFF_CHANGED, "EMPLOYEE_REMOVED", nameOf(userId));
		return summaryOf(id);
	}

	@Transactional(readOnly = true)
	public List<StaffMember> staff() {
		return jdbc.query(STAFF + " GROUP BY u.id ORDER BY name", DepartmentService::staffMember).stream()
			.map(this::withDepartments).toList();
	}

	@Transactional(readOnly = true)
	public StaffMember staffMember(UUID userId) {
		return jdbc.query(STAFF + " AND u.id = ? GROUP BY u.id", DepartmentService::staffMember, userId).stream()
			.findFirst().map(this::withDepartments)
			.orElseThrow(() -> new ResourceNotFoundException("Staff member", userId));
	}

	private static final String STAFF = """
			SELECT u.id, %s AS name, u.email, u.status, array_agg(DISTINCT r.name ORDER BY r.name) AS roles
			FROM users u
			         JOIN user_roles ur ON ur.user_id = u.id
			         JOIN roles r ON r.id = ur.role_id
			WHERE NOT EXISTS (SELECT 1 FROM user_roles i
			                           JOIN role_permissions rp ON rp.role_id = i.role_id
			                           JOIN permissions p ON p.id = rp.permission_id
			                  WHERE i.user_id = u.id AND p.code = '%s')
			""".formatted(NAME, PermissionCode.INVESTOR_PORTAL.name());

	private static StaffMember staffMember(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		return new StaffMember(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
				rs.getString("status"), List.of((String[]) rs.getArray("roles").getArray()), List.of());
	}

	private StaffMember withDepartments(StaffMember member) {
		List<StaffDepartment> assigned = jdbc.query("""
				SELECT d.id, d.code, d.name, s.primary_assignment
				FROM staff_departments s JOIN departments d ON d.id = s.department_id
				WHERE s.user_id = ? ORDER BY s.primary_assignment DESC, d.name""",
				(rs, n) -> new StaffDepartment(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
						rs.getBoolean("primary_assignment")), member.id());
		return new StaffMember(member.id(), member.name(), member.email(), member.status(), member.roles(), assigned);
	}

	private static boolean seesAll(AuthenticatedUser viewer) {
		return viewer.permissions().contains(PermissionCode.DEPARTMENT_MANAGE.name());
	}

	private boolean belongs(UUID userId, UUID departmentId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM staff_departments WHERE user_id = ? AND department_id = ?)",
				Boolean.class, userId, departmentId));
	}

	/** Investors cannot join a department: an account is either an investor or staff. */
	private void requireStaff(UUID userId) {
		if (jdbc.query(STAFF + " AND u.id = ? GROUP BY u.id", DepartmentService::staffMember, userId).isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only employees can join a department");
		}
	}

	private Department load(UUID id) {
		return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department", id));
	}

	private static void requireVersion(Department department, long version) {
		if (department.getVersion() != version) {
			throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
					"The department was changed by someone else; reload and retry");
		}
	}

	private String nameOf(UUID userId) {
		if (userId == null) {
			return "";
		}
		return jdbc.query("SELECT %s FROM users u WHERE u.id = ?".formatted(NAME), (rs, n) -> rs.getString(1), userId)
			.stream().findFirst().orElse(userId.toString());
	}

	private DepartmentSummary summaryOf(UUID id) {
		return jdbc.query(SUMMARY + " WHERE d.id = ?", DepartmentService::summary, id).getFirst();
	}

	private static DepartmentSummary summary(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		return new DepartmentSummary(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
				rs.getString("description"), rs.getBoolean("active"), rs.getObject("head_user_id", UUID.class),
				rs.getString("head_name"), rs.getLong("employees"), rs.getLong("role_count"), rs.getLong("version"));
	}

	private void record(UUID actorId, UUID departmentId, AuditAction action, String historyAction, String detail) {
		Instant now = clock.instant();
		jdbc.update("INSERT INTO department_history (department_id, action, detail, actor_id, created_at) VALUES (?, ?, ?, ?, ?)",
				departmentId, historyAction, detail, actorId, Timestamp.from(now));
		audit.record(AuditRecord.of(actorId, action, ENTITY, departmentId)
			.withNewValue(Map.of("change", historyAction, "detail", detail)));
	}

}
