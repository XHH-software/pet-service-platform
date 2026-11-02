package com.pet.platform;

import com.pet.platform.init.DemoDataInitializer;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.mapper.AppointmentMapper;
import com.pet.platform.module.appointment.mapper.AppointmentSlotMapper;
import com.pet.platform.module.appointment.mapper.ServiceDailyQuotaMapper;
import com.pet.platform.module.appointment.service.AppointmentService;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.mapper.CustomerMapper;
import com.pet.platform.module.customer.mapper.PetMapper;
import com.pet.platform.module.schedule.entity.Schedule;
import com.pet.platform.module.schedule.mapper.ScheduleMapper;
import com.pet.platform.module.schedule.service.ScheduleService;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.mapper.ServiceItemMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.mapper.StaffMapper;
import com.pet.platform.task.AppointmentTimeoutTask;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 集成测试基类：真实启动 Spring 容器，跑真实的 MyBatis-Plus + H2(MODE=MySQL)。
 *
 * <p>刻意不做 mock：这里要验证的「唯一索引防超卖」「名额令牌」都依赖数据库约束本身，
 * 用 mock 替换掉 Mapper 就等于把被测对象删掉了。
 *
 * <p>Redis 在 application-test.yml 里指向 127.0.0.1:1（必然连不上），
 * 因此每个用例都在「缓存不可用」的真实降级路径上运行。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    /** 关掉演示数据播种，避免 30 条历史预约干扰断言 */
    @MockBean
    protected DemoDataInitializer demoDataInitializer;

    /** 关掉定时任务，避免后台线程与本用例的并发场景互相污染 */
    @MockBean
    protected AppointmentTimeoutTask appointmentTimeoutTask;

    @Autowired
    protected AppointmentService appointmentService;

    @Autowired
    protected StaffMapper staffMapper;
    @Autowired
    protected CustomerMapper customerMapper;
    @Autowired
    protected PetMapper petMapper;
    @Autowired
    protected ServiceItemMapper serviceItemMapper;
    @Autowired
    protected ScheduleMapper scheduleMapper;
    @Autowired
    protected ScheduleService scheduleService;
    @Autowired
    protected AppointmentMapper appointmentMapper;
    @Autowired
    protected AppointmentSlotMapper slotMapper;
    @Autowired
    protected ServiceDailyQuotaMapper quotaMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 预约日期统一用「明天」，避开“当天已过去的时段”这条无关校验 */
    protected final LocalDate bookingDate = LocalDate.now().plusDays(1);

    @BeforeEach
    void cleanDatabase() {
        for (String table : new String[]{"appointment_slot", "service_daily_quota", "appointment_item",
                "appointment", "schedule", "pet", "customer", "service_item", "staff_specialty", "staff"}) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    // ==================== 造数据 ====================

    protected Long seedStaff() {
        Staff staff = new Staff();
        staff.setName("测试技师");
        staff.setUsername("tester_" + System.nanoTime());
        staff.setPassword("{bcrypt}placeholder");
        staff.setPhone("139" + System.nanoTime() % 100000000L);
        staff.setPosition(2);
        staff.setLevel(2);
        staff.setStatus(1);
        staffMapper.insert(staff);
        return staff.getId();
    }

    protected Long seedCustomer() {
        Customer customer = new Customer();
        customer.setNickname("测试客户");
        customer.setPhone("13800000000");
        customerMapper.insert(customer);
        return customer.getId();
    }

    protected Long seedPet(Long customerId) {
        Pet pet = new Pet();
        pet.setCustomerId(customerId);
        pet.setName("豆豆");
        pet.setSpecies(1);
        pet.setWeight(new BigDecimal("8.50"));
        pet.setVaccineExpiryDate(LocalDate.now().plusMonths(6));
        petMapper.insert(pet);
        return pet.getId();
    }

    protected Long seedService(String name, int duration, int dailyLimit) {
        ServiceItem item = new ServiceItem();
        item.setName(name);
        item.setCategoryId(1L);
        item.setPrice(new BigDecimal("99.00"));
        item.setDuration(duration);
        item.setPetSizeLimit(0);
        item.setDailyLimit(dailyLimit);
        item.setStatus(1);
        serviceItemMapper.insert(item);
        return item.getId();
    }

    protected Long seedSchedule(Long staffId, LocalTime start, LocalTime end) {
        Schedule schedule = new Schedule();
        schedule.setStaffId(staffId);
        schedule.setScheduleDate(bookingDate);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setStatus(1);
        schedule.setBookedCount(0);
        schedule.setVersion(0);
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    protected Appointment bookingRequest(Long petId, Long staffId, Long serviceItemId, LocalTime start) {
        Appointment request = new Appointment();
        request.setPetId(petId);
        request.setStaffId(staffId);
        request.setServiceItemId(serviceItemId);
        request.setAppointmentDate(bookingDate);
        request.setStartTime(start);
        return request;
    }
}
