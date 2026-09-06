export type SkillType = 'BUSINESS' | 'FINANCE' | 'TECH' | 'MARKETING' | 'LABOR';
export const SKILL_TYPES: SkillType[] = ['BUSINESS', 'FINANCE', 'TECH', 'MARKETING', 'LABOR'];

export type EducationLevel = 'NONE' | 'HIGH_SCHOOL' | 'COLLEGE' | 'BACHELOR' | 'MASTER' | 'PHD';
export const EDUCATION_ORDER: EducationLevel[] = ['NONE', 'HIGH_SCHOOL', 'COLLEGE', 'BACHELOR', 'MASTER', 'PHD'];

export type LifestyleTier = 'SPARTAN' | 'MODEST' | 'COMFORTABLE' | 'LUXURY' | 'ELITE';
export const LIFESTYLE_COSTS: Record<LifestyleTier, { monthlyCost: number; happinessBonus: number }> = {
  SPARTAN: { monthlyCost: 600, happinessBonus: -8 },
  MODEST: { monthlyCost: 1400, happinessBonus: 0 },
  COMFORTABLE: { monthlyCost: 2600, happinessBonus: 8 },
  LUXURY: { monthlyCost: 5000, happinessBonus: 16 },
  ELITE: { monthlyCost: 10000, happinessBonus: 24 }
};

export interface Job {
  id: string;
  title: string;
  tier: number;
  requiredEducation: EducationLevel;
  requiredSkill: SkillType | null;
  requiredSkillLevel: number;
  baseSalary: number;
  skillGainPerMonth: Partial<Record<SkillType, number>>;
  happinessImpact: number;
}

export const JOB_CATALOG: Job[] = [
  { id: 'JOB_CASHIER', title: 'Retail Cashier', tier: 1, requiredEducation: 'NONE', requiredSkill: null, requiredSkillLevel: 0, baseSalary: 1800, skillGainPerMonth: { LABOR: 1 }, happinessImpact: -2 },
  { id: 'JOB_DRIVER', title: 'Delivery Driver', tier: 1, requiredEducation: 'NONE', requiredSkill: 'LABOR', requiredSkillLevel: 5, baseSalary: 2200, skillGainPerMonth: { LABOR: 2 }, happinessImpact: -3 },
  { id: 'JOB_CLERK', title: 'Office Clerk', tier: 2, requiredEducation: 'HIGH_SCHOOL', requiredSkill: null, requiredSkillLevel: 0, baseSalary: 2600, skillGainPerMonth: { BUSINESS: 1 }, happinessImpact: 0 },
  { id: 'JOB_SALES', title: 'Sales Representative', tier: 2, requiredEducation: 'HIGH_SCHOOL', requiredSkill: 'MARKETING', requiredSkillLevel: 10, baseSalary: 3200, skillGainPerMonth: { MARKETING: 2 }, happinessImpact: 1 },
  { id: 'JOB_TECH_SUPPORT', title: 'IT Support Technician', tier: 2, requiredEducation: 'COLLEGE', requiredSkill: 'TECH', requiredSkillLevel: 10, baseSalary: 3600, skillGainPerMonth: { TECH: 2 }, happinessImpact: 1 },
  { id: 'JOB_ACCOUNTANT', title: 'Junior Accountant', tier: 3, requiredEducation: 'BACHELOR', requiredSkill: 'FINANCE', requiredSkillLevel: 15, baseSalary: 4800, skillGainPerMonth: { FINANCE: 2 }, happinessImpact: 3 },
  { id: 'JOB_SW_ENGINEER', title: 'Software Engineer', tier: 3, requiredEducation: 'BACHELOR', requiredSkill: 'TECH', requiredSkillLevel: 25, baseSalary: 6200, skillGainPerMonth: { TECH: 3 }, happinessImpact: 4 },
  { id: 'JOB_MARKETING_MGR', title: 'Marketing Manager', tier: 4, requiredEducation: 'BACHELOR', requiredSkill: 'MARKETING', requiredSkillLevel: 35, baseSalary: 7000, skillGainPerMonth: { MARKETING: 3 }, happinessImpact: 5 },
  { id: 'JOB_FINANCE_MGR', title: 'Finance Manager', tier: 4, requiredEducation: 'MASTER', requiredSkill: 'FINANCE', requiredSkillLevel: 40, baseSalary: 9000, skillGainPerMonth: { FINANCE: 3 }, happinessImpact: 6 },
  { id: 'JOB_SENIOR_ENGINEER', title: 'Senior Software Architect', tier: 5, requiredEducation: 'MASTER', requiredSkill: 'TECH', requiredSkillLevel: 55, baseSalary: 12000, skillGainPerMonth: { TECH: 3 }, happinessImpact: 7 },
  { id: 'JOB_EXECUTIVE', title: 'Chief Executive Officer', tier: 6, requiredEducation: 'PHD', requiredSkill: 'BUSINESS', requiredSkillLevel: 70, baseSalary: 20000, skillGainPerMonth: { BUSINESS: 4 }, happinessImpact: 10 }
];

export function findJob(id: string | null): Job | null {
  return JOB_CATALOG.find((j) => j.id === id) ?? null;
}

export function isEligibleForJob(job: Job, education: EducationLevel, skills: Record<string, number>): boolean {
  if (EDUCATION_ORDER.indexOf(education) < EDUCATION_ORDER.indexOf(job.requiredEducation)) return false;
  if (!job.requiredSkill) return true;
  return (skills[job.requiredSkill] ?? 0) >= job.requiredSkillLevel;
}

export interface EducationProgram {
  id: string;
  title: string;
  grantsLevel: EducationLevel;
  tuitionCost: number;
  durationMonths: number;
  happinessImpactPerMonth: number;
}

export const EDUCATION_CATALOG: EducationProgram[] = [
  { id: 'EDU_HS', title: 'Adult High School Program', grantsLevel: 'HIGH_SCHOOL', tuitionCost: 500, durationMonths: 4, happinessImpactPerMonth: -1 },
  { id: 'EDU_COLLEGE', title: 'Community College Certificate', grantsLevel: 'COLLEGE', tuitionCost: 3000, durationMonths: 8, happinessImpactPerMonth: -1.5 },
  { id: 'EDU_BACHELOR', title: "Bachelor's Degree Program", grantsLevel: 'BACHELOR', tuitionCost: 12000, durationMonths: 24, happinessImpactPerMonth: -2 },
  { id: 'EDU_MASTER', title: "Master's Degree Program", grantsLevel: 'MASTER', tuitionCost: 20000, durationMonths: 18, happinessImpactPerMonth: -2.5 },
  { id: 'EDU_PHD', title: 'Ph.D. Program', grantsLevel: 'PHD', tuitionCost: 35000, durationMonths: 30, happinessImpactPerMonth: -3 }
];

export function findEducationProgram(id: string | null): EducationProgram | null {
  return EDUCATION_CATALOG.find((p) => p.id === id) ?? null;
}
