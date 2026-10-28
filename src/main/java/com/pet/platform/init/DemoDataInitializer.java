package com.pet.platform.init;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.entity.AppointmentItem;
import com.pet.platform.module.appointment.entity.AppointmentSlot;
import com.pet.platform.module.appointment.mapper.AppointmentItemMapper;
import com.pet.platform.module.appointment.mapper.AppointmentMapper;
import com.pet.platform.module.appointment.mapper.AppointmentSlotMapper;
import com.pet.platform.module.appointment.service.OrderNoService;
import com.pet.platform.module.category.entity.Category;
import com.pet.platform.module.category.mapper.CategoryMapper;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.entity.CustomerAddress;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.mapper.CustomerAddressMapper;
import com.pet.platform.module.customer.mapper.CustomerMapper;
import com.pet.platform.module.customer.mapper.PetMapper;
import com.pet.platform.module.review.entity.Review;
import com.pet.platform.module.review.mapper.ReviewMapper;
import com.pet.platform.module.schedule.entity.Schedule;
import com.pet.platform.module.schedule.mapper.ScheduleMapper;
import com.pet.platform.module.service.entity.PackageDetail;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.entity.ServicePackage;
import com.pet.platform.module.service.mapper.PackageDetailMapper;
import com.pet.platform.module.service.mapper.ServiceItemMapper;
import com.pet.platform.module.service.mapper.ServicePackageMapper;
import com.pet.platform.module.shop.entity.Shop;
import com.pet.platform.module.shop.entity.ShopBusinessHour;
import com.pet.platform.module.shop.mapper.ShopBusinessHourMapper;
import com.pet.platform.module.shop.mapper.ShopMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.entity.StaffSpecialty;
import com.pet.platform.module.staff.mapper.StaffMapper;
import com.pet.platform.module.staff.mapper.StaffSpecialtyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 演示数据初始化：只在 staff 表为空时执行，日期基于“今天”，保证工作台/日历始终有数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final ShopMapper shopMapper;
    private final ShopBusinessHourMapper hourMapper;
    private final CategoryMapper categoryMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServicePackageMapper packageMapper;
    private final PackageDetailMapper packageDetailMapper;
    private final StaffMapper staffMapper;
    private final StaffSpecialtyMapper staffSpecialtyMapper;
    private final CustomerMapper customerMapper;
    private final PetMapper petMapper;
    private final CustomerAddressMapper addressMapper;
    private final ScheduleMapper scheduleMapper;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentItemMapper appointmentItemMapper;
    private final AppointmentSlotMapper appointmentSlotMapper;
    private final ReviewMapper reviewMapper;
    private final PasswordEncoder passwordEncoder;
    private final OrderNoService orderNoService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        if (staffMapper.selectCount(null) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        log.info("开始初始化宠物服务预约平台演示数据...");

        Shop shop = initShop(now);
        Map<String, Category> categories = initCategories(now);
        Map<String, ServiceItem> items = initServiceItems(categories, now);
        Map<String, ServicePackage> packages = initPackages(categories, items, now);
        Map<String, Staff> staffMap = initStaff(items, now);
        Map<String, Customer> customers = initCustomers(now);
        Map<String, Pet> pets = initPets(customers, now);
        initAddresses(customers.get("zhang"), now);
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        initSchedules(monday, now);
        initAppointments(items, packages, staffMap, pets, now);
        refreshBookedCounts();
        log.info("演示数据初始化完成：门店={}, 员工={}, 客户={}, 服务项目={}",
                shop.getName(), staffMap.size(), customers.size(), items.size());
    }

    private Shop initShop(LocalDateTime now) {
        Shop shop = new Shop();
        shop.setName("萌宠之家");
        shop.setLogo("https://picsum.photos/seed/pet-shop-logo/200/200");
        shop.setPhone("13800000000");
        shop.setAddress("辽宁省沈阳市沈北新区黄河北大街253号");
        shop.setBusinessStatus(1);
        shop.setCreateTime(now);
        shop.setUpdateTime(now);
        shopMapper.insert(shop);

        for (int i = 1; i <= 7; i++) {
            ShopBusinessHour hour = new ShopBusinessHour();
            hour.setShopId(shop.getId());
            hour.setDayOfWeek(i);
            hour.setIsRest(i == 7);
            hour.setOpenTime(LocalTime.of(9, 0));
            hour.setCloseTime(i == 6 ? LocalTime.of(21, 0) : LocalTime.of(20, 0));
            hourMapper.insert(hour);
        }
        return shop;
    }

    private Map<String, Category> initCategories(LocalDateTime now) {
        Map<String, Category> map = new HashMap<>();
        map.put("wash", category("洗护", 1, 1, now));
        map.put("beauty", category("美容", 1, 2, now));
        map.put("care", category("保健", 1, 3, now));
        map.put("pkgWash", category("洗护套餐", 2, 1, now));
        map.put("pkgBeauty", category("美容套餐", 2, 2, now));
        return map;
    }

    private Category category(String name, int type, int sort, LocalDateTime now) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setSort(sort);
        category.setStatus(1);
        category.setCreateTime(now);
        category.setUpdateTime(now);
        categoryMapper.insert(category);
        return category;
    }

    private Map<String, ServiceItem> initServiceItems(Map<String, Category> categories, LocalDateTime now) {
        Map<String, ServiceItem> map = new HashMap<>();
        map.put("smallWash", item("小型犬基础洗护", categories.get("wash").getId(), "128.00", 60, 1,
                "包含洗澡、吹干、基础修剪、耳道清洁", "dog-small", now));
        map.put("mediumWash", item("中型犬基础洗护", categories.get("wash").getId(), "168.00", 60, 2,
                "包含洗澡、吹干、基础修剪、指甲修剪", "dog-medium", now));
        map.put("largeWash", item("大型犬基础洗护", categories.get("wash").getId(), "228.00", 90, 3,
                "大型犬专属洗护，含深层清洁与吹干", "dog-large", now));
        map.put("catWash", item("猫咪精致洗护", categories.get("wash").getId(), "158.00", 50, 0,
                "猫咪温和洗护，含去浮毛与耳道清洁", "cat-wash", now));
        map.put("haircut", item("精剪造型", categories.get("beauty").getId(), "198.00", 90, 0,
                "专业造型师一对一修剪，可指定造型", "haircut", now));
        map.put("medicated", item("药浴护理", categories.get("care").getId(), "138.00", 45, 0,
                "针对皮肤问题的药浴护理", "medicated", now));
        map.put("teeth", item("口腔清洁", categories.get("care").getId(), "88.00", 30, 0,
                "去除牙结石，清新口气", "teeth", now));
        map.put("nails", item("指甲修剪", categories.get("care").getId(), "38.00", 20, 0,
                "修剪指甲并打磨，保护爱宠关节", "nails", now));
        return map;
    }

    private ServiceItem item(String name, Long categoryId, String price, int duration, int sizeLimit,
                             String description, String seed, LocalDateTime now) {
        ServiceItem item = new ServiceItem();
        item.setName(name);
        item.setCategoryId(categoryId);
        item.setPrice(new BigDecimal(price));
        item.setDuration(duration);
        item.setPetSizeLimit(sizeLimit);
        item.setDailyLimit(10);
        item.setDescription(description);
        item.setImage("https://picsum.photos/seed/" + seed + "/600/400");
        item.setStatus(1);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        serviceItemMapper.insert(item);
        return item;
    }

    private Map<String, ServicePackage> initPackages(Map<String, Category> categories,
                                                     Map<String, ServiceItem> items, LocalDateTime now) {
        Map<String, ServicePackage> map = new HashMap<>();
        map.put("washCard", pkg("洗护两次卡", categories.get("pkgWash").getId(), "336.00", "288.00",
                "两次中型犬基础洗护，可分次使用", "pkg-wash",
                Arrays.asList(detail(items.get("mediumWash"), 2)), now));
        map.put("beautySet", pkg("美容焕新套餐", categories.get("pkgBeauty").getId(), "324.00", "268.00",
                "精剪造型 + 口腔清洁 + 指甲修剪", "pkg-beauty",
                Arrays.asList(detail(items.get("haircut"), 1), detail(items.get("teeth"), 1),
                        detail(items.get("nails"), 1)), now));
        return map;
    }

    private PackageDetail detail(ServiceItem item, int quantity) {
        PackageDetail detail = new PackageDetail();
        detail.setServiceItemId(item.getId());
        detail.setQuantity(quantity);
        return detail;
    }

    private ServicePackage pkg(String name, Long categoryId, String originalPrice, String sellingPrice,
                               String description, String seed, List<PackageDetail> details,
                               LocalDateTime now) {
        ServicePackage pkg = new ServicePackage();
        pkg.setName(name);
        pkg.setCategoryId(categoryId);
        pkg.setOriginalPrice(new BigDecimal(originalPrice));
        pkg.setSellingPrice(new BigDecimal(sellingPrice));
        pkg.setDailyLimit(5);
        pkg.setDescription(description);
        pkg.setImage("https://picsum.photos/seed/" + seed + "/600/400");
        pkg.setStatus(1);
        pkg.setCreateTime(now);
        pkg.setUpdateTime(now);
        packageMapper.insert(pkg);
        for (PackageDetail detail : details) {
            detail.setPackageId(pkg.getId());
            packageDetailMapper.insert(detail);
        }
        return pkg;
    }

    private Map<String, Staff> initStaff(Map<String, ServiceItem> items, LocalDateTime now) {
        Map<String, Staff> map = new HashMap<>();
        map.put("manager", staff("张伟", "admin", "13800000001", 1, 3, "staff-1", now));
        map.put("wang", staff("王芳", "13800000002", "13800000002", 2, 3, "staff-2", now));
        map.put("li", staff("李娜", "13800000003", "13800000003", 2, 2, "staff-3", now));
        map.put("sun", staff("孙悦", "13800000004", "13800000004", 2, 1, "staff-4", now));
        map.put("zhao", staff("赵敏", "13800000005", "13800000005", 3, 1, "staff-5", now));

        specialty(map.get("wang"), items.get("mediumWash"), items.get("largeWash"), items.get("haircut"));
        specialty(map.get("li"), items.get("smallWash"), items.get("catWash"), items.get("medicated"));
        specialty(map.get("sun"), items.get("mediumWash"), items.get("haircut"), items.get("teeth"),
                items.get("nails"));
        specialty(map.get("zhao"), items.get("smallWash"), items.get("teeth"), items.get("nails"));
        return map;
    }

    private Staff staff(String name, String username, String phone, int position, int level,
                        String seed, LocalDateTime now) {
        Staff staff = new Staff();
        staff.setName(name);
        staff.setUsername(username);
        staff.setPassword(passwordEncoder.encode("123456"));
        staff.setPhone(phone);
        staff.setPosition(position);
        staff.setLevel(level);
        staff.setStatus(1);
        staff.setAvatar("https://picsum.photos/seed/" + seed + "/200/200");
        staff.setCreateTime(now);
        staff.setUpdateTime(now);
        staffMapper.insert(staff);
        return staff;
    }

    private void specialty(Staff staff, ServiceItem... items) {
        for (ServiceItem item : items) {
            StaffSpecialty specialty = new StaffSpecialty();
            specialty.setStaffId(staff.getId());
            specialty.setServiceItemId(item.getId());
            staffSpecialtyMapper.insert(specialty);
        }
    }

    private Map<String, Customer> initCustomers(LocalDateTime now) {
        Map<String, Customer> map = new HashMap<>();
        map.put("zhang", customer("张女士", "mock_openid_dev", "13812341234", "cust-1", now));
        map.put("li", customer("李先生", "mock_openid_2", "13912345678", "cust-2", now));
        map.put("wang", customer("王女士", "mock_openid_3", "13712345678", "cust-3", now));
        map.put("liu", customer("刘先生", "mock_openid_4", "13612345678", "cust-4", now));
        map.put("chen", customer("陈女士", "mock_openid_5", "13512345678", "cust-5", now));
        return map;
    }

    private Customer customer(String nickname, String openid, String phone, String seed, LocalDateTime now) {
        Customer customer = new Customer();
        customer.setNickname(nickname);
        customer.setOpenid(openid);
        customer.setPhone(phone);
        customer.setAvatar("https://picsum.photos/seed/" + seed + "/200/200");
        customer.setCreateTime(now);
        customer.setUpdateTime(now);
        customerMapper.insert(customer);
        return customer;
    }

    private Map<String, Pet> initPets(Map<String, Customer> customers, LocalDateTime now) {
        Map<String, Pet> map = new HashMap<>();
        map.put("doudou", pet(customers.get("zhang"), "豆豆", 1, "金毛", 1, "25.50",
                LocalDate.now().plusMonths(8), "性格温顺，怕吹风机", now));
        map.put("mimi", pet(customers.get("zhang"), "咪咪", 2, "英国短毛猫", 2, "4.20",
                LocalDate.now().plusMonths(5), "比较胆小", now));
        map.put("wangcai", pet(customers.get("li"), "旺财", 1, "柯基", 1, "12.00",
                LocalDate.now().plusMonths(10), "喜欢和人玩", now));
        map.put("buding", pet(customers.get("wang"), "布丁", 1, "泰迪", 2, "5.00",
                LocalDate.now().plusMonths(3), "对鸡肉过敏", now));
        map.put("xueqiu", pet(customers.get("liu"), "雪球", 1, "萨摩耶", 1, "22.00",
                LocalDate.now().minusDays(12), "疫苗已过期，需要提醒客户", now));
        map.put("kele", pet(customers.get("chen"), "可乐", 1, "拉布拉多", 1, "28.00",
                LocalDate.now().plusMonths(6), "食欲很好，注意控制零食", now));
        return map;
    }

    private Pet pet(Customer customer, String name, int species, String breed, int gender, String weight,
                    LocalDate vaccine, String remark, LocalDateTime now) {
        Pet pet = new Pet();
        pet.setCustomerId(customer.getId());
        pet.setName(name);
        pet.setSpecies(species);
        pet.setBreed(breed);
        pet.setGender(gender);
        pet.setWeight(new BigDecimal(weight));
        pet.setBirthday(LocalDate.now().minusYears(2).minusMonths(ThreadLocalRandom.current().nextInt(1, 10)));
        pet.setNeutered(true);
        pet.setVaccineExpiryDate(vaccine);
        pet.setAllergies("");
        pet.setRemark(remark);
        pet.setAvatar("https://picsum.photos/seed/pet-" + name.hashCode() + "/300/300");
        pet.setCreateTime(now);
        pet.setUpdateTime(now);
        petMapper.insert(pet);
        return pet;
    }

    private void initAddresses(Customer customer, LocalDateTime now) {
        CustomerAddress address = new CustomerAddress();
        address.setCustomerId(customer.getId());
        address.setProvince("辽宁省");
        address.setCity("沈阳市");
        address.setDistrict("沈北新区");
        address.setDetail("黄河北大街 253 号 3 号楼 2 单元 501");
        address.setContactName("张女士");
        address.setContactPhone("13812341234");
        address.setIsDefault(true);
        address.setCreateTime(now);
        address.setUpdateTime(now);
        addressMapper.insert(address);

        CustomerAddress second = new CustomerAddress();
        second.setCustomerId(customer.getId());
        second.setProvince("辽宁省");
        second.setCity("沈阳市");
        second.setDistrict("皇姑区");
        second.setDetail("长江街 12 号 1 单元 802");
        second.setContactName("张先生");
        second.setContactPhone("13900001111");
        second.setIsDefault(false);
        second.setCreateTime(now);
        second.setUpdateTime(now);
        addressMapper.insert(second);
    }

    /**
     * 初始化两周前到两周后的排班，周日休息，孙悦周一休息。
     */
    private void initSchedules(LocalDate monday, LocalDateTime now) {
        List<Staff> staffList = staffMapper.selectList(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getStatus, 1)
                .ne(Staff::getPosition, 1));
        for (int offset = -14; offset <= 14; offset++) {
            LocalDate date = LocalDate.now().plusDays(offset);
            for (Staff staff : staffList) {
                boolean rest = date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY
                        || (staff.getPhone().endsWith("4") && date.getDayOfWeek() == java.time.DayOfWeek.MONDAY);
                Schedule schedule = new Schedule();
                schedule.setStaffId(staff.getId());
                schedule.setScheduleDate(date);
                schedule.setStartTime(LocalTime.of(9, 0));
                schedule.setEndTime(LocalTime.of(19, 0));
                schedule.setStatus(rest ? 2 : 1);
                schedule.setBookedCount(0);
                schedule.setVersion(0);
                schedule.setCreateTime(now);
                schedule.setUpdateTime(now);
                scheduleMapper.insert(schedule);
            }
        }
    }

    /**
     * 历史 7 天 + 今天 + 明天，生成不同状态的预约，保证工作台、日历、排行榜都有数据。
     */
    private void initAppointments(Map<String, ServiceItem> items, Map<String, ServicePackage> packages,
                                  Map<String, Staff> staffMap, Map<String, Pet> pets, LocalDateTime now) {
        List<Staff> servers = Arrays.asList(staffMap.get("wang"), staffMap.get("li"),
                staffMap.get("sun"), staffMap.get("zhao"));
        List<Pet> petList = Arrays.asList(pets.get("doudou"), pets.get("mimi"), pets.get("wangcai"),
                pets.get("buding"), pets.get("xueqiu"), pets.get("kele"));
        LocalTime[] times = {LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(14, 0),
                LocalTime.of(16, 0), LocalTime.of(18, 0)};
        int[] scores = {5, 5, 4, 5, 4};

        for (int offset = -6; offset <= 1; offset++) {
            LocalDate date = LocalDate.now().plusDays(offset);
            int count = offset == 1 ? 2 : 4;
            for (int i = 0; i < count; i++) {
                Staff staff = servers.get((i + Math.abs(offset)) % servers.size());
                Pet pet = petList.get((i + Math.abs(offset) + 1) % petList.size());
                LocalTime start = times[(i + 1) % times.length];
                ServiceItem item = chooseItem(items, pet, offset, i);
                ServicePackage pkg = offset == -3 && i == 0 ? packages.get("washCard") : null;

                int status;
                if (offset < 0) {
                    status = (i == 0 && offset % 2 == 0) ? 6 : 5;
                } else if (offset == 0) {
                    status = i == 0 ? 1 : (i == 1 ? 2 : (i == 2 ? 3 : 5));
                } else {
                    status = 2;
                }
                if (status == 6 && offset == 0) {
                    status = 5;
                }

                Appointment appointment = buildAppointment(date, start, status, staff, pet, item, pkg, now);
                if (offset == 0 && status == 1) {
                    appointment.setCreateTime(now.minusMinutes(8));
                }
                appointmentMapper.insert(appointment);

                insertItems(appointment, item, pkg);
                insertSlots(appointment, start);

                if (status == 6 || (status == 5 && i == 1 && offset < 0)) {
                    insertReview(appointment, pet, 6, scores[Math.abs(offset + i) % scores.length],
                            offset % 2 == 0, now);
                }
            }
        }
    }

    private ServiceItem chooseItem(Map<String, ServiceItem> items, Pet pet, int offset, int index) {
        if (pet.getSpecies() != null && pet.getSpecies() == 2) {
            return items.get("catWash");
        }
        if (pet.getWeight() != null && pet.getWeight().doubleValue() > 25) {
            return index % 2 == 0 ? items.get("largeWash") : items.get("haircut");
        }
        if (pet.getWeight() != null && pet.getWeight().doubleValue() > 10) {
            return index % 2 == 0 ? items.get("mediumWash") : items.get("haircut");
        }
        return index % 2 == 0 ? items.get("smallWash") : items.get("nails");
    }

    private Appointment buildAppointment(LocalDate date, LocalTime start, int status, Staff staff, Pet pet,
                                         ServiceItem item, ServicePackage pkg, LocalDateTime now) {
        int duration = pkg == null ? item.getDuration() : 60 * 2;
        BigDecimal amount = pkg == null ? item.getPrice() : pkg.getSellingPrice();
        Appointment appointment = new Appointment();
        appointment.setOrderNo(orderNoService.next(date));
        appointment.setCustomerId(pet.getCustomerId());
        appointment.setPetId(pet.getId());
        appointment.setStaffId(staff.getId());
        appointment.setAppointmentDate(date);
        appointment.setStartTime(start);
        appointment.setEndTime(start.plusMinutes(duration));
        appointment.setTotalAmount(amount);
        appointment.setPaidAmount(status == 1 ? BigDecimal.ZERO : amount);
        appointment.setPayStatus(status == 1 ? 0 : 1);
        appointment.setStatus(status);
        appointment.setSource(1);
        appointment.setRemark("");
        appointment.setCreateTime(LocalDateTime.of(date, start).minusHours(2));
        appointment.setUpdateTime(LocalDateTime.of(date, start).minusHours(2));
        if (status != 1) {
            appointment.setPayTime(LocalDateTime.of(date, start).minusMinutes(30));
        }
        if (status == 4) {
            appointment.setCancelReason("客户临时有事");
        }
        return appointment;
    }

    private void insertItems(Appointment appointment, ServiceItem item, ServicePackage pkg) {
        AppointmentItem appointmentItem = new AppointmentItem();
        appointmentItem.setAppointmentId(appointment.getId());
        if (pkg != null) {
            appointmentItem.setPackageId(pkg.getId());
            appointmentItem.setServiceName(pkg.getName());
            appointmentItem.setPrice(pkg.getSellingPrice());
            appointmentItem.setDuration(120);
            appointmentItem.setQuantity(1);
            appointmentItem.setSubtotal(pkg.getSellingPrice());
        } else {
            appointmentItem.setServiceItemId(item.getId());
            appointmentItem.setServiceName(item.getName());
            appointmentItem.setPrice(item.getPrice());
            appointmentItem.setDuration(item.getDuration());
            appointmentItem.setQuantity(1);
            appointmentItem.setSubtotal(item.getPrice());
        }
        appointmentItemMapper.insert(appointmentItem);
    }

    private void insertSlots(Appointment appointment, LocalTime start) {
        for (LocalTime time = start; time.isBefore(appointment.getEndTime()); time = time.plusMinutes(30)) {
            AppointmentSlot slot = new AppointmentSlot();
            slot.setAppointmentId(appointment.getId());
            slot.setStaffId(appointment.getStaffId());
            slot.setAppointmentDate(appointment.getAppointmentDate());
            slot.setSlotTime(time);
            appointmentSlotMapper.insert(slot);
        }
    }

    private void insertReview(Appointment appointment, Pet pet, int status, int score,
                              boolean replied, LocalDateTime now) {
        Review review = new Review();
        review.setAppointmentId(appointment.getId());
        review.setCustomerId(appointment.getCustomerId());
        review.setStaffId(appointment.getStaffId());
        review.setScore(score);
        review.setContent(score >= 5 ? "服务非常细致，狗狗洗完香喷喷的，下次还来！"
                : "整体不错，美容师很有耐心，环境也很干净。");
        review.setImagesJson("[\"https://picsum.photos/seed/review-" + appointment.getId() + "/300/300\"]");
        if (replied) {
            review.setReplyContent("感谢您的认可，期待您和宝贝再次光临~");
            review.setReplyTime(LocalDateTime.of(appointment.getAppointmentDate(),
                    LocalTime.of(10, 30)).plusDays(1));
        }
        review.setCreateTime(LocalDateTime.of(appointment.getAppointmentDate(), LocalTime.of(10, 30))
                .plusHours(1));
        review.setUpdateTime(review.getCreateTime());
        reviewMapper.insert(review);
        if (appointment.getStatus() != 6) {
            appointment.setStatus(6);
            appointmentMapper.updateById(appointment);
        }
    }

    private void refreshBookedCounts() {
        List<Schedule> schedules = scheduleMapper.selectList(null);
        for (Schedule schedule : schedules) {
            long count = appointmentSlotMapper.selectCount(Wrappers.<AppointmentSlot>lambdaQuery()
                    .eq(AppointmentSlot::getStaffId, schedule.getStaffId())
                    .eq(AppointmentSlot::getAppointmentDate, schedule.getScheduleDate()));
            Schedule update = new Schedule();
            update.setId(schedule.getId());
            update.setBookedCount((int) count);
            update.setVersion(schedule.getVersion());
            scheduleMapper.updateById(update);
        }
    }
}
