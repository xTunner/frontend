class Ability
  include CanCan::Ability

  def initialize(user)
    user ||= User.new # guest user (not logged in)

    cannot :manage, :all # default to no access

    can :manage, User, :id => user.id # only users may check out their own page

    can :manage, Project do |p|
      p.user_ids.include? user.id
    end

    can :read, Build do |b|
      b.project.user_ids.include? user.id
    end

    can :manage, :all if user.admin?

    can :manage, System if user.admin?

    # can do |action, subject_class, subject|
    #   puts action
    #   puts subject_class
    #   puts subject
    #   if not subject.nil? then
    #     subject.each do |s|
    #       puts s
    #     end
    #   end
    # end

    # The first argument to `can` is the action you are giving the user permission to do.
    # If you pass :manage it will apply to every action. Other common actions here are
    # :read, :create, :update and :destroy.
    #
    # The second argument is the resource the user can perform the action on. If you pass
    # :all it will apply to every resource. Otherwise pass a Ruby class of the resource.
    #
    # The third argument is an optional hash of conditions to further filter the objects.
    # For example, here the user can only update published articles.
    #
    #   can :update, Article, :published => true
    #
    # See the wiki for details: https://github.com/ryanb/cancan/wiki/Defining-Abilities
  end
end
